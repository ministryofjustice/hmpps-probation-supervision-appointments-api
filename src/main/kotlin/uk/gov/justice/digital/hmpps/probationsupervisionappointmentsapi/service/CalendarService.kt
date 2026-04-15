package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import com.microsoft.graph.models.Attendee
import com.microsoft.graph.models.AttendeeType
import com.microsoft.graph.models.BodyType
import com.microsoft.graph.models.DateTimeTimeZone
import com.microsoft.graph.models.EmailAddress
import com.microsoft.graph.models.Event
import com.microsoft.graph.models.ItemBody
import com.microsoft.graph.serviceclient.GraphServiceClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.config.SmsLanguage
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.AppointmentType
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.EventRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.Recipient
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.RescheduleEventRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.DeliusOutlookMappingsResponse
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.EventResponse
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.DeliusOutlookMapping
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.DeliusOutlookMappingRepository
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.NotificationMapping
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.NotificationMappingRepository
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.getSupervisionAppointmentUrn
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.SmsUtil.Companion.APPOINTMENT_DATE
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.SmsUtil.Companion.APPOINTMENT_LOCATION
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.SmsUtil.Companion.APPOINTMENT_TIME
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.SmsUtil.Companion.APPOINTMENT_TYPE
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.SmsUtil.Companion.FIRST_NAME
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.util.EnglishToWelshTranslator
import uk.gov.service.notify.NotificationClient
import uk.gov.service.notify.NotificationClientException
import java.time.LocalDateTime.parse
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

private const val EVENT_TIMEZONE = "Europe/London"
private val UK_ZONE = ZoneId.of(EVENT_TIMEZONE)

@Service
class CalendarService(
  private val graphClient: GraphServiceClient,
  private val deliusOutlookMappingRepository: DeliusOutlookMappingRepository,
  private val notificationMappingRepository: NotificationMappingRepository,
  private val featureFlagsService: FeatureFlagsService,
  private val notificationClient: NotificationClient,
  private val telemetryService: TelemetryService,
  private val templateResolverService: SmsTemplateResolverService,
  private val domainEventService: DomainEventService,
  @Value("\${calendar-from-email}") private val fromEmail: String,
  @Value("\${outlook-environment}") private val outLookEnv: String,
) {

  fun sendEvent(eventRequest: EventRequest): EventResponse? {
    val now = ZonedDateTime.now()

    if (eventRequest.start.isBefore(now)) {
      val telemetryProperties = mapOf(
        "supervisionAppointmentUrn" to eventRequest.supervisionAppointmentUrn,
      )
      telemetryService.trackEvent("AppointmentInThePast", telemetryProperties)
      return EventResponse(
        id = null,
        subject = eventRequest.subject,
        startDate = eventRequest.start
          .withZoneSameInstant(UK_ZONE)
          .toOffsetDateTime()
          .toString(),
        endDate = eventRequest.start
          .plusMinutes(eventRequest.durationInMinutes)
          .withZoneSameInstant(UK_ZONE)
          .toOffsetDateTime()
          .toString(),
        attendees = eventRequest.recipients.map { it.emailAddress },
      )
    }

    val event = buildEvent(eventRequest)
    val response = createEvent(fromEmail, event)

    return response?.id?.let { id ->
      deliusOutlookMappingRepository.save(
        DeliusOutlookMapping(
          supervisionAppointmentUrn = eventRequest.supervisionAppointmentUrn,
          outlookId = id,
        ),
      )

      telemetryService.trackEvent(
        "AppointmentCalendarEventCreationSuccessful",
        mapOf("supervisionAppointmentUrn" to eventRequest.supervisionAppointmentUrn),
      )

      sendSMSNotification(eventRequest)
      response.toEventResponse()
    } ?: run {
      telemetryService.trackEvent(
        "AppointmentCalendarEventCreationFailed",
        mapOf("supervisionAppointmentUrn" to eventRequest.supervisionAppointmentUrn),
      )
      null
    }
  }

  fun sendSMSNotification(eventRequest: EventRequest) {
    if (eventRequest.smsEventRequest?.smsOptIn == true &&
      featureFlagsService.isEnabledForUser(
        "sms-notification-toggle",
        eventRequest.recipients.first().emailAddress,
      )
    ) {
      sendSms(eventRequest, buildTemplateValues(eventRequest, SmsLanguage.ENGLISH), SmsLanguage.ENGLISH)

      // WELSH sms
      if (eventRequest.smsEventRequest.includeWelshTranslation) {
        sendSms(eventRequest, buildTemplateValues(eventRequest, SmsLanguage.WELSH), SmsLanguage.WELSH)
      }
    }
  }

  fun buildTemplateValues(eventRequest: EventRequest, smsLanguage: SmsLanguage): Map<String, String> {
    val englishDate = eventRequest.start.toNotifyDate()
    val date = if (smsLanguage == SmsLanguage.WELSH) {
      englishDate
        .split(" ")
        .joinToString(" ") { EnglishToWelshTranslator.toWelsh(it) }
    } else {
      englishDate
    }

    return mapOf(
      FIRST_NAME to eventRequest.smsEventRequest?.firstName.orEmpty(),
      APPOINTMENT_DATE to date,
      APPOINTMENT_TIME to eventRequest.start.toNotifyTime(),
      APPOINTMENT_LOCATION to eventRequest.smsEventRequest?.appointmentLocation.orEmpty(),
      APPOINTMENT_TYPE to getAppointmentType(eventRequest),
    )
  }

  private fun getAppointmentType(eventRequest: EventRequest): String {
    val type = AppointmentType.fromCode(eventRequest.smsEventRequest?.appointmentTypeCode)
    return (
      if (eventRequest.smsEventRequest?.includeWelshTranslation == true) {
        type?.welsh
      } else {
        type?.english
      }
      ).orEmpty()
  }

  fun sendSms(
    eventRequest: EventRequest,
    templateValues: Map<String, String> = emptyMap(),
    smsLanguage: SmsLanguage,
  ) {
    val telemetryProperties = mapOf(
      "crn" to eventRequest.smsEventRequest!!.crn,
      "supervisionAppointmentUrn" to eventRequest.supervisionAppointmentUrn,
      "smsLanguage" to smsLanguage.name,
    )

    try {
      val template = templateResolverService.getTemplate(smsLanguage, eventRequest.smsEventRequest.appointmentLocation)
      notificationClient.sendSms(
        template.id.toString(),
        eventRequest.smsEventRequest.mobileNumber,
        templateValues,
        eventRequest.smsEventRequest.crn,
      ).also {
        notificationMappingRepository.save(
          NotificationMapping(
            deliusExternalReference = eventRequest.supervisionAppointmentUrn,
            notificationId = it.notificationId,
            templateId = it.templateId,
            message = it.body,
          ),
        )

        domainEventService.buildAndPublishContactEvent(
          crn = eventRequest.smsEventRequest.crn,
          notificationId = it.notificationId,
        )

        telemetryService.trackEvent("AppointmentReminderSent", telemetryProperties)
      }
    } catch (e: Exception) {
      when (e) {
        is NotificationClientException ->
          trackTelemetry(e, "AppointmentReminderFailureNotificationClientException", telemetryProperties)
        is IllegalArgumentException ->
          trackTelemetry(e, "AppointmentReminderFailureInvalidArgument", telemetryProperties)
        is DataAccessException ->
          trackTelemetry(e, "AppointmentReminderFailureNotificationMappingDatabaseFailure", telemetryProperties)
        else ->
          trackTelemetry(e, "AppointmentReminderFailure", telemetryProperties)
      }
    }
  }

  private fun trackTelemetry(exception: Exception, eventName: String, telemetryProperties: Map<String, String>) {
    telemetryService.trackEvent(eventName, telemetryProperties)
    telemetryService.trackException(exception, telemetryProperties)
  }

  fun rescheduleEvent(rescheduleEventRequest: RescheduleEventRequest): EventResponse? {
    rescheduleEventRequest.oldSupervisionAppointmentUrn?.let {
      deleteExistingOutlookEvent(it)
    }

    return sendEvent(rescheduleEventRequest.rescheduledEventRequest)
  }

  fun deleteExistingOutlookEvent(oldSupervisionAppointmentUrn: String) {
    findEventDetails(oldSupervisionAppointmentUrn)?.let { event ->
      val eventId = event.id ?: return

      val eventStart = OffsetDateTime.parse(requireNotNull(event.startDate))
        .atZoneSameInstant(UK_ZONE)

      val now = ZonedDateTime.now()

      if (eventStart.isAfter(now) || eventStart.isEqual(now)) {
        graphClient.users()
          .byUserId(fromEmail)
          .calendar()
          .events()
          .byEventId(eventId)
          .delete()
      }
    }
  }

  fun buildEvent(eventRequest: EventRequest) = Event().apply {
    subject = eventRequest.subject
    val startInUk = eventRequest.start
      .withZoneSameInstant(UK_ZONE)

    val endInUk = eventRequest.start
      .plusMinutes(eventRequest.durationInMinutes)
      .withZoneSameInstant(UK_ZONE)

    start = DateTimeTimeZone().apply {
      timeZone = EVENT_TIMEZONE
      dateTime = startInUk.toLocalDateTime().toString()
    }

    end = DateTimeTimeZone().apply {
      timeZone = EVENT_TIMEZONE
      dateTime = endInUk.toLocalDateTime().toString()
    }
    attendees = if (outLookEnv != "prod") {
      getAttendees(
        listOf(
          Recipient(
            fromEmail,
            fromEmail.substringBefore("@"),
          ),
        ),
      )
    } else {
      getAttendees(eventRequest.recipients)
    }
    body = ItemBody().apply {
      contentType = BodyType.Html
      content = eventRequest.message
    }
  }

  fun getAttendees(recipients: List<Recipient>) = recipients.map {
    Attendee().apply {
      emailAddress = EmailAddress().apply {
        address = it.emailAddress
        name = it.name
      }
      type = AttendeeType.Required
    }
  }

  fun getEventDetailsMappings(supervisionAppointmentUrn: String): DeliusOutlookMappingsResponse {
    val mapping = deliusOutlookMappingRepository.getSupervisionAppointmentUrn(supervisionAppointmentUrn)

    return mapping.toDeliusOutlookMappingsResponse()
  }

  fun createEvent(userEmail: String, event: Event): Event? = graphClient
    .users()
    .byUserId(userEmail)
    .calendar()
    .events()
    .post(event)

  fun getEventDetails(supervisionAppointmentUrn: String): EventResponse? {
    // if no mapping found, exception will be thrown
    val outlookId = deliusOutlookMappingRepository.getSupervisionAppointmentUrn(supervisionAppointmentUrn).outlookId

    // user may have deleted the event, so handle null response
    val event = getCalendarEventByOutlookId(outlookId)

    return event?.toEventResponse()
  }

  fun findEventDetails(supervisionAppointmentUrn: String): EventResponse? {
    // if no mapping found, null will be returned
    val outlookId = deliusOutlookMappingRepository.findBySupervisionAppointmentUrn(supervisionAppointmentUrn)?.outlookId

    // user may have deleted the event, so handle null response
    val event = outlookId?.let { getCalendarEventByOutlookId(it) }

    return event?.toEventResponse()
  }

  fun getCalendarEventByOutlookId(outlookId: String): Event? = graphClient
    .users()
    .byUserId(fromEmail)
    .calendar()
    .events()
    .byEventId(outlookId)[
    { requestConfiguration ->
      requestConfiguration?.queryParameters?.select = arrayOf(
        "subject",
        "organizer",
        "attendees",
        "start",
        "end",
      )
      requestConfiguration.headers.add("Prefer", "outlook.timezone=\"Europe/London\"")
    },
  ]
}

fun Event.toEventResponse(): EventResponse = EventResponse(
  id = id,
  subject = subject,
  startDate = start?.let {
    OffsetDateTime.parse(it.dateTime)
      .toString()
  },
  endDate = end?.let {
    OffsetDateTime.parse(it.dateTime)
      .toString()
  },
  attendees = attendees?.mapNotNull { it?.emailAddress?.address },
)

fun DeliusOutlookMapping.toDeliusOutlookMappingsResponse(): DeliusOutlookMappingsResponse = DeliusOutlookMappingsResponse(
  supervisionAppointmentUrn = supervisionAppointmentUrn,
  outlookId = outlookId,
  createdAt = createdAt.toString(),
  updatedAt = updatedAt.toString(),
)

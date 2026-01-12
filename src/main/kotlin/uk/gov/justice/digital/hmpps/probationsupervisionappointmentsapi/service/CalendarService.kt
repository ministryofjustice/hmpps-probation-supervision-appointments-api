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
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.EventRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.Recipient
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.RescheduleEventRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.DeliusOutlookMappingsResponse
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.EventResponse
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.DeliusOutlookMapping
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.DeliusOutlookMappingRepository
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.getSupervisionAppointmentUrn
import uk.gov.service.notify.NotificationClient
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private const val EVENT_TIMEZONE = "Europe/London"

@Service
class CalendarService(
  private val graphClient: GraphServiceClient,
  private val deliusOutlookMappingRepository: DeliusOutlookMappingRepository,
  private val featureFlagsService: FeatureFlagsService,
  private val notificationClient: NotificationClient,
  private val telemetryService: TelemetryService,
  @Value("\${calendar-from-email}") private val fromEmail: String,
  @Value("\${govuk-notify.template-id}") private val appointmentScheduledTemplateId: String,
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
        startDate = eventRequest.start.toString(),
        endDate = eventRequest.start.plusMinutes(eventRequest.durationInMinutes).toString(),
        attendees = eventRequest.recipients.map { it.emailAddress },
      )
    }

    val event = buildEvent(eventRequest)
    val response = createEvent(fromEmail, event)
    deliusOutlookMappingRepository.save(
      DeliusOutlookMapping(
        supervisionAppointmentUrn = eventRequest.supervisionAppointmentUrn,
        outlookId = response?.id.toString(),
      ),
    )

    sendSMSNotification(eventRequest)

    return response?.toEventResponse()
  }

  fun sendSMSNotification(eventRequest: EventRequest) {
    if (eventRequest.smsEventRequest?.smsOptIn == true && featureFlagsService.isEnabled("sms-notification-toggle")) {
      val templateValues = mapOf(
        "FirstName" to eventRequest.smsEventRequest.firstName,
        "Date" to eventRequest.start.format(DateTimeFormatter.ofPattern("d MMMM yyyy 'at' h:mma")),
      )

      val telemetryProperties = mapOf(
        "crn" to eventRequest.smsEventRequest.crn,
        "supervisionAppointmentUrn" to eventRequest.supervisionAppointmentUrn,
      )

      try {
        notificationClient.sendSms(
          appointmentScheduledTemplateId,
          eventRequest.smsEventRequest.mobileNumber,
          templateValues,
          eventRequest.smsEventRequest.crn,
        )

        telemetryService.trackEvent("AppointmentReminderSent", telemetryProperties)
      } catch (e: Exception) {
        telemetryService.trackEvent("AppointmentReminderFailure", telemetryProperties)
        telemetryService.trackException(e, telemetryProperties)
      }
    }
  }

  fun rescheduleEvent(rescheduleEventRequest: RescheduleEventRequest): EventResponse? {
    deleteExistingOutlookEvent(rescheduleEventRequest.oldSupervisionAppointmentUrn)

    return sendEvent(rescheduleEventRequest.rescheduledEventRequest)
  }

  fun deleteExistingOutlookEvent(oldSupervisionAppointmentUrn: String) {
    findEventDetails(oldSupervisionAppointmentUrn)?.let {
      val eventStart = LocalDateTime.parse(requireNotNull(it.startDate))
        .atZone(ZoneId.of("UTC"))
      val now = ZonedDateTime.now()

      if (eventStart.isAfter(now) || eventStart.isEqual(now)) {
        graphClient.users()
          .byUserId(fromEmail)
          .calendar()
          .events()
          .byEventId(it.id)
          .delete()
      }
    }
  }

  fun buildEvent(eventRequest: EventRequest) = Event().apply {
    subject = eventRequest.subject
    start = DateTimeTimeZone().apply {
      timeZone = EVENT_TIMEZONE
      dateTime = eventRequest.start.toString()
    }
    end = DateTimeTimeZone().apply {
      dateTime = eventRequest.start.plusMinutes(eventRequest.durationInMinutes).toString()
      timeZone = EVENT_TIMEZONE
    }
    attendees = getAttendees(eventRequest.recipients)
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
    val event = getEvent(outlookId)

    return event?.toEventResponse()
  }

  fun findEventDetails(supervisionAppointmentUrn: String): EventResponse? {
    // if no mapping found, null will be returned
    val outlookId = deliusOutlookMappingRepository.findBySupervisionAppointmentUrn(supervisionAppointmentUrn)?.outlookId

    // user may have deleted the event, so handle null response
    val event = outlookId?.let { getEvent(it) }

    return event?.toEventResponse()
  }

  fun getEvent(outlookId: String): Event? = graphClient
    .users()
    .byUserId("MPoP-Digital-Team@justice.gov.uk")
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
  startDate = start?.dateTime,
  endDate = end?.dateTime,
  attendees = attendees?.mapNotNull { it?.emailAddress?.address },
)

fun DeliusOutlookMapping.toDeliusOutlookMappingsResponse(): DeliusOutlookMappingsResponse = DeliusOutlookMappingsResponse(
  supervisionAppointmentUrn,
  outlookId,
  createdAt.toString(),
  updatedAt.toString(),
)

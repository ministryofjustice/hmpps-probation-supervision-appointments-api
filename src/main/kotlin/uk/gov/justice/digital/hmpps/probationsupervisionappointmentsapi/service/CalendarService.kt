package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import com.microsoft.graph.models.Attendee
import com.microsoft.graph.models.AttendeeType
import com.microsoft.graph.models.BodyType
import com.microsoft.graph.models.DateTimeTimeZone
import com.microsoft.graph.models.EmailAddress
import com.microsoft.graph.models.Event
import com.microsoft.graph.models.ItemBody
import com.microsoft.graph.serviceclient.GraphServiceClient
import io.sentry.Sentry
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
  private val featureFlags: FeatureFlags,
  private val notificationClient: NotificationClient,
  private val telemetryService: TelemetryService,
  @Value("\${calendar-from-email}") private val fromEmail: String,
) {

  fun sendEvent(eventRequest: EventRequest): EventResponse? {
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
    if (eventRequest.smsEventRequest?.smsOptIn == true && featureFlags.enabled("sms-notification-toggle")) {
      val templateValues = mapOf(
        "FirstName" to eventRequest.smsEventRequest.personName,
        "NextWorkSession" to eventRequest.start.format(DateTimeFormatter.ofPattern("d MMMM yyyy 'at' h:mma")),
      )

      val telemetryProperties = mapOf(
        "crn" to eventRequest.smsEventRequest.crn,
      )

      try {
        notificationClient.sendSms(
          "1234",
          eventRequest.smsEventRequest.mobileNumber,
          templateValues,
          eventRequest.smsEventRequest.crn,
        )
      } catch (e: Exception) {
        telemetryService.trackEvent("UnpaidWorkAppointmentReminderFailure", telemetryProperties)
        telemetryService.trackException(e, telemetryProperties)
        Sentry.captureException(e)
      }
    }
  }

  fun rescheduleEvent(rescheduleEventRequest: RescheduleEventRequest): EventResponse? {
    deleteExistingOutlookEvent(rescheduleEventRequest.oldSupervisionAppointmentUrn)

    val now = ZonedDateTime.now()
    val eventRequest = rescheduleEventRequest.rescheduledEventRequest

    if (eventRequest.start.isAfter(now) || eventRequest.start.isEqual(now)) {
      return sendEvent(eventRequest)
    }
    return EventResponse(
      id = null,
      subject = eventRequest.subject,
      startDate = eventRequest.start.toString(),
      endDate = eventRequest.start.plusMinutes(eventRequest.durationInMinutes).toString(),
      attendees = eventRequest.recipients.map { it.emailAddress },
    )
  }

  fun deleteExistingOutlookEvent(oldSupervisionAppointmentUrn: String) {
    getEventDetails(oldSupervisionAppointmentUrn)?.let {
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
    val outlookId = deliusOutlookMappingRepository.getSupervisionAppointmentUrn(supervisionAppointmentUrn).outlookId

    // user may have deleted their outlook event
    val event = graphClient
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

    return event?.toEventResponse()
  }
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

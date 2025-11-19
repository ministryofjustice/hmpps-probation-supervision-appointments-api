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
import java.time.ZonedDateTime

private const val EVENT_TIMEZONE = "Europe/London"

@Service
class CalendarService(
  val graphClient: GraphServiceClient,
  val deliusOutlookMappingRepository: DeliusOutlookMappingRepository,
  @Value("\${calendar-from-email}")
  private val fromEmail: String,
) {

  fun sendEvent(eventRequest: EventRequest): EventResponse {
    val event = buildEvent(eventRequest)
    val response = createEvent(fromEmail, event)
    deliusOutlookMappingRepository.save(
      DeliusOutlookMapping(
        supervisionAppointmentUrn = eventRequest.supervisionAppointmentUrn,
        outlookId = response.id.toString(),
      ),
    )

    return response.toEventResponse()
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
      val eventStart = ZonedDateTime.parse(it.startDate)
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

  fun createEvent(userEmail: String, event: Event) = graphClient
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
        requestConfiguration.queryParameters.select = arrayOf(
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
  startDate = start.dateTime,
  endDate = end.dateTime,
  attendees = attendees.map { it.emailAddress.address },
)

fun DeliusOutlookMapping.toDeliusOutlookMappingsResponse(): DeliusOutlookMappingsResponse = DeliusOutlookMappingsResponse(
  supervisionAppointmentUrn,
  outlookId,
  createdAt.toString(),
  updatedAt.toString(),
)

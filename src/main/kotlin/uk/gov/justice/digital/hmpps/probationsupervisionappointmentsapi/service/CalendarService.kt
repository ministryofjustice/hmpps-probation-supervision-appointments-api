package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import com.microsoft.graph.models.Attendee
import com.microsoft.graph.models.AttendeeType
import com.microsoft.graph.models.BodyType
import com.microsoft.graph.models.DateTimeTimeZone
import com.microsoft.graph.models.EmailAddress
import com.microsoft.graph.models.Event
import com.microsoft.graph.models.ItemBody
import com.microsoft.graph.serviceclient.GraphServiceClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.EventRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.DeliusOutlookMappingsResponse
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.EventResponse
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.exception.NotFoundException
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.DeliusOutlookMapping
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.DeliusOutlookMappingRepository

@Service
class CalendarService(
  val graphServiceClient: GraphServiceClient,
  val deliusOutlookMappingRepository: DeliusOutlookMappingRepository,
) {

  fun sendEvent(eventRequest: EventRequest): EventResponse {
    val eventTimezone = "Europe/London"

    val eventStart = DateTimeTimeZone().apply {
      timeZone = eventTimezone
      dateTime = eventRequest.start.toString()
    }

    val eventEnd = DateTimeTimeZone().apply {
      dateTime = eventRequest.start.plusMinutes(eventRequest.duration).toString()
      timeZone = eventTimezone
    }

    val eventAttendees = eventRequest.recipients.map {
      Attendee().apply {
        emailAddress = EmailAddress().apply {
          address = it.emailAddress
          name = it.name
        }
        type = AttendeeType.Required
      }
    }

    val eventBody = ItemBody().apply {
      contentType = BodyType.Text
      content = eventRequest.message
    }

    val event = Event().apply {
      subject = eventRequest.subject
      start = eventStart
      end = eventEnd
      attendees = eventAttendees
      body = eventBody
    }

    val response = graphServiceClient
      .users()
      .byUserId(eventRequest.fromEmail)
      .calendar()
      .events()
      .post(event)

    deliusOutlookMappingRepository.save(
      DeliusOutlookMapping(
        supervisionAppointmentUrn = eventRequest.supervisionAppointmentUrn,
        outlookId = response.id.toString(),
      ),
    )

    return response.toEventResponse()
  }

  fun getEventDetailsMappings(supervisionAppointmentUrn: String): DeliusOutlookMappingsResponse {
    val mapping = deliusOutlookMappingRepository
      .findBySupervisionAppointmentUrn(supervisionAppointmentUrn)
      ?: throw NotFoundException("DeliusOutlookMapping", "supervisionAppointmentUrn", supervisionAppointmentUrn)

    return mapping.toDeliusOutlookMappingsResponse()
  }
}

fun Event.toEventResponse(): EventResponse = EventResponse(
  id,
  subject,
  start.dateTime,
  end.dateTime,
  attendees.map { it.emailAddress.address },
)

fun DeliusOutlookMapping.toDeliusOutlookMappingsResponse(): DeliusOutlookMappingsResponse = DeliusOutlookMappingsResponse(
  supervisionAppointmentUrn,
  outlookId,
  createdAt.toString(),
  updatedAt.toString(),
)

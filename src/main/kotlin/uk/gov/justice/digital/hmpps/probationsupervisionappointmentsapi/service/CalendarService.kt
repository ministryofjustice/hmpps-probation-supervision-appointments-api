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

@Service
class CalendarService(val graphServiceClient: GraphServiceClient) {

  fun sendEvent(eventRequest: EventRequest) {
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

    graphServiceClient
      .users()
      .byUserId(eventRequest.fromEmail)
      .calendar()
      .events()
      .post(event)
  }
}

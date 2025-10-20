package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import com.microsoft.graph.models.Event
import com.microsoft.graph.serviceclient.GraphServiceClient
import org.springframework.stereotype.Service

@Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
@Service
class MicrosoftGraphCalendarClient(
  private val graphClient: GraphServiceClient
) : CalendarClient {
  override fun createEvent(userEmail: String, event: Event): Event {
    return graphClient
      .users()
      .byUserId(userEmail)
      .calendar()
      .events()
      .post(event)
  }
}


interface CalendarClient {
  fun createEvent(userEmail: String, event: Event): Event
}
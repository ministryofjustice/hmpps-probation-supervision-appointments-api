package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.api

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.EventResponse
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.IntegrationTestBase

class SaveEventIntegrationTest : IntegrationTestBase() {

  @Test
  fun `unauthorized status returned`() {
    webTestClient.post().uri("/calendar/event")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `test successful creation of event`() {
    val email = "test@test.com"
    stubGraphCreateEvent(email)

    val durationMinutes: Long = 30

    val body = mapOf(
      "fromEmail" to email,
      "recipients" to listOf(mapOf("emailAddress" to "attendee@example.com", "name" to "Attendee")),
      "message" to "Meeting with Jon",
      "subject" to "3 Way Meeting (Non NS) with Jon Smith",
      "start" to "2025-09-16T10:00:00",
      "duration" to durationMinutes,
    )

    val expected = EventResponse("mock-event-id-123", "3 Way Meeting (Non NS) with Jon Smith", "2025-09-16T10:00:00", "2025-09-16T10:30:00", listOf("test@test.com"))
    webTestClient.post().uri("/calendar/event")
      .headers(setAuthorisation())
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(body)
      .exchange()
      .expectStatus().isCreated
      .expectBody(EventResponse::class.java)
      .isEqualTo(expected)
  }
}

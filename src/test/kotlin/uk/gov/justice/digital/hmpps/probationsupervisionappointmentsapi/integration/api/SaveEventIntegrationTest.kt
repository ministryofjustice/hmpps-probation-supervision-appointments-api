package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.EventResponse
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.getByDeliusExternalReference

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
    val deliusExternalReference = "urn:uk:gov:hmpps:manage-supervision-service:appointment:8afbd895-c8e7-4a49-8eaa-3149243e7931"

    val body = mapOf(
      "fromEmail" to email,
      "recipients" to listOf(mapOf("emailAddress" to "attendee@example.com", "name" to "Attendee")),
      "message" to "Meeting with Jon",
      "subject" to "3 Way Meeting (Non NS) with Jon Smith",
      "start" to "2025-09-16T10:00:00",
      "duration" to durationMinutes,
      "deliusExternalReference" to deliusExternalReference,
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

    val mapping = deliusOutlookMappingRepository.getByDeliusExternalReference(deliusExternalReference)

    assertEquals(body["deliusExternalReference"], mapping.deliusExternalReference)
  }
}

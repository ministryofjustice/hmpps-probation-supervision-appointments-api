package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.api

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.DeliusOutlookMappingsResponse
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.IntegrationTestBase

class GetEventIntegrationTest : IntegrationTestBase() {

  @Test
  fun `unauthorized status returned`() {
    webTestClient.get().uri("/calendar/event")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `test successful retrieval of event mapping`() {
    val supervisionAppointmentUrn = "urn:uk:gov:hmpps:manage-supervision-service:appointment:8afbd895-c8e7-4a49-8eaa-3149243e7931"
    val outlookId = "mock-event-id-123"

    // Insert test data into the repository
    deliusOutlookMappingRepository.save(
      uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.DeliusOutlookMapping(
        supervisionAppointmentUrn = supervisionAppointmentUrn,
        outlookId = outlookId
      )
    )

    webTestClient.get()
      .uri { uriBuilder ->
        uriBuilder.path("/calendar/event")
          .queryParam("supervisionAppointmentUrn", supervisionAppointmentUrn)
          .build()
      }
      .headers(setAuthorisation())
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody(DeliusOutlookMappingsResponse::class.java)
      .consumeWith { response ->
        val mappings = response.responseBody?.mappings
        assert(mappings?.size == 1)
        assert(mappings?.first()?.supervisionAppointmentUrn == supervisionAppointmentUrn)
        assert(mappings?.first()?.outlookId == outlookId)
      }
  }
}

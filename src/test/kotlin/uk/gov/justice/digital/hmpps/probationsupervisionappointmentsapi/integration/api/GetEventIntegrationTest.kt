package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.api

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.DeliusOutlookMappingsResponse
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.DeliusOutlookMapping

class GetEventIntegrationTest : IntegrationTestBase() {

  @BeforeEach
  fun setUp() {
    deliusOutlookMappingRepository.deleteAll()
  }

  @Test
  fun `unauthorized status returned`() {
    webTestClient.get().uri("/calendar/event-mapping")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `test successful retrieval of event mapping by supervisionAppointmentUrn`() {
    val supervisionAppointmentUrn =
      "urn:uk:gov:hmpps:manage-supervision-service:appointment:a9bf7a2c-b713-43e3-a8b7-62e31526a001"
    val outlookId = "mock-outlook-id-5678"

    deliusOutlookMappingRepository.save(
      DeliusOutlookMapping(
        supervisionAppointmentUrn = supervisionAppointmentUrn,
        outlookId = outlookId,
      ),
    )

    webTestClient.get()
      .uri { uriBuilder ->
        uriBuilder.path("/calendar/event-mapping")
          .queryParam("supervisionAppointmentUrn", supervisionAppointmentUrn)
          .build()
      }
      .headers(setAuthorisation())
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody(DeliusOutlookMappingsResponse::class.java)
      .consumeWith { response ->
        val body = response.responseBody!!
        assert(body.outlookId == outlookId)
        assert(body.supervisionAppointmentUrn == supervisionAppointmentUrn)
      }
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "nonexistent-outlook-id",
      "urn:uk:gov:hmpps:manage-supervision-service:appointment:00000000-0000-0000-0000-000000000000",
    ],
  )
  fun `supervisionAppointmentUrn not found`(supervisionAppointmentUrn: String) {
    val trimmed = supervisionAppointmentUrn.trim()

    webTestClient.get()
      .uri { uriBuilder ->
        uriBuilder.path("/calendar/event-mapping")
          .queryParam("supervisionAppointmentUrn", supervisionAppointmentUrn)
          .build()
      }
      .headers(setAuthorisation())
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isNotFound
      .expectBody()
      .jsonPath("$.userMessage").value<String> { message ->
        assert(message == "Not found: DeliusOutlookMapping with supervisionAppointmentUrn of $trimmed not found")
      }
  }
}

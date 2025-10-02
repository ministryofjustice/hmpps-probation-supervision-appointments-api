package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.api

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.DeliusOutlookMappingsResponse
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.DeliusOutlookMapping

class GetEventIntegrationTest : IntegrationTestBase() {

  @Test
  fun `unauthorized status returned`() {
    webTestClient.get().uri("/calendar/event")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `test successful retrieval of event mapping`() {
    val supervisionAppointmentUrn =
      "urn:uk:gov:hmpps:manage-supervision-service:appointment:8afbd895-c8e7-4a49-8eaa-3149243e7931"
    val outlookId = "mock-event-id-1234"

    deliusOutlookMappingRepository.save(
      DeliusOutlookMapping(
        supervisionAppointmentUrn = supervisionAppointmentUrn,
        outlookId = outlookId,
      ),
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
        val body = response.responseBody!!
        assert(body.supervisionAppointmentUrn == supervisionAppointmentUrn)
        assert(body.outlookId == outlookId)
      }
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "urn:uk:gov:hmpps:manage-supervision-service:appointment:nonexistent-urn",
      "",
    ],
  )
  fun `bad request when supervisionAppointmentUrn is nonexistent or blank`(urn: String) {
    webTestClient.get()
      .uri { uriBuilder ->
        uriBuilder.path("/calendar/event")
          .queryParam("supervisionAppointmentUrn", urn)
          .build()
      }
      .headers(setAuthorisation())
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isNotFound
      .expectBody()
      .jsonPath("$.userMessage").value<String> { message ->
        assert(message.contains("DeliusOutlookMapping with supervisionAppointmentUrn of"))
      }
  }

}

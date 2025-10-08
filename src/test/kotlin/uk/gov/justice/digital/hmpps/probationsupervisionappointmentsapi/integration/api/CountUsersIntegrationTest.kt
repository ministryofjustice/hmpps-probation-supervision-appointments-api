package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.api

import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.IntegrationTestBase

class CountUsersIntegrationTest : IntegrationTestBase() {

  @Test
  fun `unauthorized status returned`() {
    webTestClient.post().uri("/users/count")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `test successful creation of event`() {
    stubGraphUserCount()

    webTestClient.get().uri("/users/count")
      .headers(setAuthorisation())
      .exchange()
      .expectStatus().isOk
      .expectBody(Int::class.java)
      .isEqualTo(1)
  }
}

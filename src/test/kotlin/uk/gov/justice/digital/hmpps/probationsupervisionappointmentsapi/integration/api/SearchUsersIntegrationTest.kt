package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.api

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.FilteredUsers
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.IntegrationTestBase

class SearchUsersIntegrationTest : IntegrationTestBase() {

  @Test
  fun `unauthorized status returned`() {
    webTestClient.post().uri("/users/search")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @ParameterizedTest()
  @ValueSource(strings = ["/users/search", "/users/search?query=user"])
  fun `test successful creation of event`(uri: String) {
    stubGraphGetUsers()

    val expected = FilteredUsers(listOf("user.one@test.com - Developer", "user.two@test.com"))
    webTestClient.get().uri(uri)
      .headers(setAuthorisation())
      .exchange()
      .expectStatus().isOk
      .expectBody(FilteredUsers::class.java)
      .equals(expected)
  }
}

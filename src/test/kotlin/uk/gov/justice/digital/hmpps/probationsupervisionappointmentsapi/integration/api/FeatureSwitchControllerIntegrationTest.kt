package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.api

import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.FeatureFlagsService

class FeatureSwitchControllerIntegrationTest: IntegrationTestBase() {

  @MockitoBean
  lateinit var featureFlagsService: FeatureFlagsService

  @Test
  fun `unauthorized status returned`() {
    webTestClient.post().uri("/feature-switch/isFeatureEnabledForUser")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `successful response for a given user when feature switch is enabled for user`() {

    whenever { featureFlagsService.isEnabledForUser("featureSwitchName","test@test.com") }.thenReturn(true)

    webTestClient.get().uri {
      it.path("/feature-switch/isFeatureEnabledForUser")
      .queryParam("email", "test@test.com")
      .queryParam("featureSwitchName", "featureSwitchName")
      .build() }
      .headers(setAuthorisation())
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("$.enabled").isEqualTo(true)
  }

  @Test
  fun `when a feature switch is not enabled for a user, false is returned`() {
    webTestClient.get().uri {
      it.path("/feature-switch/isFeatureEnabledForUser")
        .queryParam("email", "test@test.com")
        .queryParam("featureSwitchName", "featureSwitchName")
        .build() }
      .headers(setAuthorisation())
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("$.enabled").isEqualTo(false)
  }
}
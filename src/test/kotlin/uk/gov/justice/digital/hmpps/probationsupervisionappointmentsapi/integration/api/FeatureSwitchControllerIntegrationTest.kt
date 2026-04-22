package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.api

import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.FeatureSwitchEnabledForUserRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.FeatureSwitchResponse
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.FeatureFlagsService

class FeatureSwitchControllerIntegrationTest : IntegrationTestBase() {

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
    whenever { featureFlagsService.isEnabledForUser("featureSwitchName", "test@test.com") }.thenReturn(true)
    webTestClient.post().uri("/feature-switch/isFeatureEnabledForUser")
      .headers(setAuthorisation())
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(FeatureSwitchEnabledForUserRequest("test@test.com", "featureSwitchName"))
      .exchange()
      .expectStatus().isOk
      .expectBody(FeatureSwitchResponse::class.java)
      .isEqualTo(FeatureSwitchResponse(true))
  }

  @Test
  fun `when a feature switch is not enabled for a user, false is returned`() {
    webTestClient.post().uri("/feature-switch/isFeatureEnabledForUser")
      .headers(setAuthorisation())
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(FeatureSwitchEnabledForUserRequest("test@test.com", "featureSwitchName"))
      .exchange()
      .expectStatus().isOk
      .expectBody(FeatureSwitchResponse::class.java)
      .isEqualTo(FeatureSwitchResponse(false))
  }
}

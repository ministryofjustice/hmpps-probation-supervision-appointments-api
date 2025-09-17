package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration

import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.wiremock.MsGraphApiExtension
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.wiremock.MsGraphApiExtension.Companion.msGraph
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.wiremock.MsGraphTestConfig
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper

@ExtendWith(HmppsAuthApiExtension::class, MsGraphApiExtension::class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(classes = [MsGraphTestConfig::class])
abstract class IntegrationTestBase {

  @Autowired
  protected lateinit var webTestClient: WebTestClient

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthorisationHelper

  internal fun setAuthorisation(
    username: String? = "AUTH_ADM",
    roles: List<String> = listOf("ROLE_PROBATION_API__PROBATION_SUPERVISION_APPOINTMENTS__EVENTS"),
    scopes: List<String> = listOf("read"),
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisationHeader(username = username, scope = scopes, roles = roles)

  protected fun stubPingWithResponse(status: Int) {
    hmppsAuth.stubHealthPing(status)
  }

  protected fun stubGraphCreateEvent(email: String) {
    msGraph.stubCreateEvent(email)
  }
}

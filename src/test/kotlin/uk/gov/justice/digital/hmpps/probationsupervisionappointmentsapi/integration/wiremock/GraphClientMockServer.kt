package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.common.Json
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class MsGraphApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {

  companion object {
    @JvmField
    val msGraph = MsGraphMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    msGraph.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    msGraph.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    msGraph.stop()
  }
}

class MsGraphMockServer :
  WireMockServer(
    WireMockConfiguration.options()
      .port(WIREMOCK_PORT)
      .notifier(ConsoleNotifier(true)),
  ) {
  companion object {
    private const val WIREMOCK_PORT = 8091
  }

  fun stubCreateEvent(email: String) {
    val body = mapOf(
      "id" to "mock-event-id-123",
      "subject" to "3 Way Meeting (Non NS) with Jon Smith",
      "start" to mapOf("dateTime" to "2025-09-16T10:00:00", "timeZone" to "UTC"),
      "end" to mapOf("dateTime" to "2025-09-16T10:30:00", "timeZone" to "UTC"),
      "attendees" to listOf(mapOf("emailAddress" to mapOf("address" to email, "name" to "test"))),
    )

    stubFor(
      post(urlPathMatching("/v1.0/users/[^/]+/calendar/events"))
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(Json.write(body)),
        ),
    )
  }

  fun stubUserCount() {
    stubFor(
      get(urlPathEqualTo("/v1.0/users/\$count"))
        .withHeader("ConsistencyLevel", equalTo("eventual"))
        .withQueryParam("\$filter", equalTo("accountEnabled eq true and userType eq 'Member'"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "text/plain")
            .withBody("1"),
        ),
    )
  }

  fun stubHealthPing(status: Int) {
    stubFor(
      get(urlEqualTo("/graph/health/ping")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status)
          .withBody(if (status == 200) """{"status":"UP"}""" else """{"status":"DOWN"}"""),
      ),
    )
  }
}

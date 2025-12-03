package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class FliptExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {

  companion object {
    @JvmField
    val flipt = FliptMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    flipt.start()
    flipt.stubGetFeatureFlags()
    println("WIREMOCK IS ON PORT = ${flipt.port()}")
  }

  override fun beforeEach(context: ExtensionContext) {
    flipt.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    flipt.stop()
  }
}

class FliptMockServer :
  WireMockServer(
    WireMockConfiguration.options()
      .port(WIREMOCK_PORT)
      .notifier(ConsoleNotifier(true)),
  ) {
  companion object {
    private const val WIREMOCK_PORT = 8092
  }

  fun stubGetFeatureFlags() {
    stubFor(
      get(
        urlPathMatching("/internal/v1/evaluation/snapshot/namespace/probation-supervision"),
      )
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(
              """
                    {
                      "namespace": { "key": "probation-supervision" },
                      "flags": [
                        {
                          "key": "sms-notification-toggle",
                          "name": "sms-notification-toggle",
                          "description": "",
                          "enabled": true,
                          "type": "BOOLEAN_FLAG_TYPE",
                          "createdAt": "2025-11-25T15:28:37.920581Z",
                          "updatedAt": "2025-11-25T17:06:39.269084Z",
                          "rules": [],
                          "rollouts": []
                        }
                      ]
                    }
              """.trimIndent(),
            ),
        ),
    )
  }
}

package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.stubs

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestComponent
import java.util.UUID

@TestComponent
class GovNotifyStubs {

  @Autowired
  private lateinit var wiremock: WireMockServer

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  fun stubSendSms(
    templateId: String,
    phoneNumber: String,
    notificationId: String = UUID.randomUUID().toString(),
  ) {
    wiremock.stubFor(
      post(urlEqualTo("/v2/notifications/sms"))
        .withRequestBody(matchingJsonPath("$.template_id", equalTo(templateId)))
        .withRequestBody(matchingJsonPath("$.phone_number", equalTo(phoneNumber)))
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(
              objectMapper.writeValueAsString(
                mapOf(
                  "id" to notificationId,
                  "reference" to "X123456",
                  "content" to mapOf(
                    "body" to "Reminder: Dear John Doe...",
                    "from_number" to "447700900000",
                  ),
                  "uri" to "https://api.notifications.service.gov.uk/v2/notifications/$notificationId",
                  "template" to mapOf(
                    "id" to templateId,
                    "version" to 1,
                    "uri" to "https://api.notifications.service.gov.uk/v2/templates/$templateId",
                  ),
                ),
              ),
            ),
        ),
    )
  }

  fun stubGetTemplate(templateId: String) {
    wiremock.stubFor(
      get(urlEqualTo("/v2/templates/$templateId"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(
              objectMapper.writeValueAsString(
                mapOf(
                  "id" to templateId,
                  "name" to "Appointment reminder",
                  "type" to "sms",
                  "created_at" to "2024-01-01T10:00:00Z",
                  "version" to 1,
                  "body" to "Reminder: Dear ((FIRST_NAME))...",
                ),
              ),
            ),
        ),
    )
  }
}

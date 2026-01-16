package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.api

import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.AppointmentType
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.SmsPreviewRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.SmsPreviewResponse
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.IntegrationTestBase
import uk.gov.service.notify.NotificationClient
import uk.gov.service.notify.Template
import java.time.ZonedDateTime

class SmsPreviewIntegrationTest : IntegrationTestBase() {
  private val fixedStartDateTime: ZonedDateTime = ZonedDateTime.parse("2050-01-01T10:00:00Z")

  @MockBean
  lateinit var notificationClient: NotificationClient

  private fun notifyTemplateJson(body: String): String =
    """
      {
        "id": "ff94ee5e-6799-441c-9c95-e777b3978297",
        "name": "Appointment reminder",
        "type": "sms",
        "created_at": "2024-01-01T10:00:00Z",
        "updated_at": null,
        "version": 1,
        "body": "$body",
        "subject": null,
        "letter_contact_block": null,
        "personalisation": null
      }
    """.trimIndent()

  @Test
  fun `unauthorised request returns 401`() {
    webTestClient.post()
      .uri("/sms/preview")
      .contentType(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `returns english preview only`() {
    whenever(notificationClient.getTemplateById("7a3c8a69-30fb-4361-8a3b-125be59aeddf"))
      .thenReturn(
        Template(
          notifyTemplateJson("EN ((FIRST_NAME)) ((APPOINTMENT_DATE)) ((APPOINTMENT_TIME))"),
        ),
      )

    val request = SmsPreviewRequest(
      firstName = "John",
      dateAndTimeOfAppointment = fixedStartDateTime,
      appointmentLocation = null,
      appointmentTypeCode = AppointmentType.PlannedOfficeVisitNS.code,
      includeWelshPreview = false,
    )

    webTestClient.post()
      .uri("/sms/preview")
      .headers(setAuthorisation())
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(request)
      .exchange()
      .expectStatus().isOk
      .expectBody(SmsPreviewResponse::class.java)
      .consumeWith { response ->
        val body = response.responseBody!!
        assert(body.englishSmsPreview == "EN John Saturday 1 January 10am")
        assert(body.welshSmsPreview == null)
      }
  }

  @Test
  fun `returns english and welsh preview when requested`() {
    whenever(notificationClient.getTemplateById("4280ea18-1fc6-4356-80a7-d5f0ed8aac4a"))
      .thenReturn(
        Template(
          notifyTemplateJson("EN ((FIRST_NAME)) ((APPOINTMENT_DATE)) ((APPOINTMENT_TIME)) at ((APPOINTMENT_LOCATION))"),
        ),
      )

    whenever(notificationClient.getTemplateById("b562b777-0931-4f9d-bd6f-ef99a23bdef9"))
      .thenReturn(
        Template(
          notifyTemplateJson("CY ((FIRST_NAME)) ((APPOINTMENT_DATE)) ((APPOINTMENT_TIME)) yn ((APPOINTMENT_LOCATION))"),
        ),
      )

    val request = SmsPreviewRequest(
      firstName = "John",
      dateAndTimeOfAppointment = fixedStartDateTime,
      appointmentLocation = "Leeds Office",
      appointmentTypeCode = AppointmentType.HomeVisitToCaseNS.code,
      includeWelshPreview = true,
    )

    webTestClient.post()
      .uri("/sms/preview")
      .headers(setAuthorisation())
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(request)
      .exchange()
      .expectStatus().isOk
      .expectBody(SmsPreviewResponse::class.java)
      .consumeWith { response ->
        val body = response.responseBody!!
        assert(body.englishSmsPreview == "EN John Saturday 1 January 10am at Leeds Office")
        assert(body.welshSmsPreview == "CY John Dydd Sadwrn 1 Ionawr 10am yn Leeds Office")
      }
  }
}

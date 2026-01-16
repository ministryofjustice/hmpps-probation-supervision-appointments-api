package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.config.SmsLanguage
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.AppointmentType
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.SmsPreviewRequest
import uk.gov.service.notify.Template
import java.time.ZonedDateTime

@ExtendWith(MockitoExtension::class)
class SmsPreviewServiceTest {

  @Mock
  private lateinit var translationService: TranslationService

  @Mock
  private lateinit var smsTemplateResolverService: SmsTemplateResolverService

  private lateinit var service: SmsPreviewService

  private val fixedStartDateTime: ZonedDateTime =
    ZonedDateTime.parse("2050-01-01T10:00:00Z")

  @BeforeEach
  fun setup() {
    service = SmsPreviewService(
      smsTemplateResolverService = smsTemplateResolverService,
      translationService = translationService,
    )
  }

  @Test
  fun `should return english preview only`() {
    val request = SmsPreviewRequest(
      firstName = "John",
      dateAndTimeOfAppointment = fixedStartDateTime,
      appointmentLocation = null,
      appointmentTypeCode = AppointmentType.PlannedOfficeVisitNS.code,
      includeWelshPreview = false,
    )

    whenever(
      smsTemplateResolverService.getTemplate(SmsLanguage.ENGLISH, null),
    ).thenReturn(
      Template(
        notifyTemplateJson(
          "Reminder: Dear ((FIRST_NAME)). Appointment on ((APPOINTMENT_DATE)) at ((APPOINTMENT_TIME)).",
        ),
      ),
    )

    val response = service.generatePreview(request)

    assertEquals(
      "Reminder: Dear John. Appointment on Saturday 1 January at 10am.",
      response.englishSmsPreview,
    )
    assertNull(response.welshSmsPreview)
  }

  @Test
  fun `should return english and welsh preview with location`() {
    // Welsh translation defaults to English in tests
    whenever(translationService.toWelsh(any()))
      .thenAnswer { it.arguments[0] as String }

    val request = SmsPreviewRequest(
      firstName = "John",
      dateAndTimeOfAppointment = fixedStartDateTime,
      appointmentLocation = "Leeds Office",
      appointmentTypeCode = AppointmentType.HomeVisitToCaseNS.code,
      includeWelshPreview = true,
    )

    whenever(
      smsTemplateResolverService.getTemplate(SmsLanguage.ENGLISH, "Leeds Office"),
    ).thenReturn(
      Template(
        notifyTemplateJson(
          "EN ((FIRST_NAME)) ((APPOINTMENT_DATE)) ((APPOINTMENT_TIME)) at ((APPOINTMENT_LOCATION))",
        ),
      ),
    )

    whenever(
      smsTemplateResolverService.getTemplate(SmsLanguage.WELSH, "Leeds Office"),
    ).thenReturn(
      Template(
        notifyTemplateJson(
          "CY ((FIRST_NAME)) ((APPOINTMENT_DATE)) ((APPOINTMENT_TIME)) yn ((APPOINTMENT_LOCATION))",
        ),
      ),
    )

    val response = service.generatePreview(request)

    assertEquals(
      "EN John Saturday 1 January 10am at Leeds Office",
      response.englishSmsPreview,
    )

    assertEquals(
      "CY John Saturday 1 January 10am yn Leeds Office",
      response.welshSmsPreview,
    )
  }

  @Test
  fun `formats notify date and time correctly`() {
    val dt = ZonedDateTime.parse("2026-08-11T14:00:00+01:00[Europe/London]")

    assertEquals("Tuesday 11 August", dt.toNotifyDate())
    assertEquals("2pm", dt.toNotifyTime())
  }
}

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

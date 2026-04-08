package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.config.SmsLanguage
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.AppointmentType
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.SmsPreviewRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.exception.NotFoundException
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.NotificationMapping
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.NotificationMappingRepository
import uk.gov.service.notify.Template
import java.time.ZonedDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class SmsServiceTest {

  @Mock
  private lateinit var smsTemplateResolverService: SmsTemplateResolverService

  @Mock
  private lateinit var notificationMappingRepository: NotificationMappingRepository
  private lateinit var service: SmsService

  private val fixedStartDateTime: ZonedDateTime =
    ZonedDateTime.parse("2050-01-01T10:00:00Z")

  @BeforeEach
  fun setup() {
    service = SmsService(
      smsTemplateResolverService = smsTemplateResolverService,
      notificationMappingRepository = notificationMappingRepository,
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
      "CY John Dydd Sadwrn 1 Ionawr 10am yn Leeds Office",
      response.welshSmsPreview,
    )
  }

  @Test
  fun `formats notify date and time correctly`() {
    val dt = ZonedDateTime.parse("2026-08-11T14:00:00+01:00[Europe/London]")

    assertEquals("Tuesday 11 August", dt.toNotifyDate())
    assertEquals("2pm", dt.toNotifyTime())
  }

  @Test
  fun `formats notify date and time with minutes`() {
    val dt = ZonedDateTime.parse("2026-08-11T14:30:00+01:00[Europe/London]")

    assertEquals("Tuesday 11 August", dt.toNotifyDate())
    assertEquals("2:30pm", dt.toNotifyTime())
  }

  @Test
  fun `should return sms message when notification mapping exists`() {
    val notificationId = UUID.randomUUID()
    val templateId = UUID.randomUUID()
    val expectedMessage = "[DEV]Dear Aubrey,\n" +
      "You have an appointment at Wrexham Team Office on Wednesday 25 March at 3:15pm.\n" +
      "This is an automated message. Do not reply."
    val deliusExternalReference = "urn:uk:gov:hmpps:manage-supervision-service:appointment:e9c73ac0-ad16-42de-8e72-16c868c078x1"
    val mapping = NotificationMapping(
      id = 100L,
      deliusExternalReference = deliusExternalReference,
      notificationId = notificationId,
      message = expectedMessage,
      templateId = templateId,
    )

    whenever(
      notificationMappingRepository.findByNotificationId(notificationId),
    ).thenReturn(mapping)

    val result = service.getSmsByNotificationId(notificationId)

    assertEquals(expectedMessage, result.smsMessage)
    assertEquals(deliusExternalReference, result.deliusExternalReference)
    verify(notificationMappingRepository).findByNotificationId(notificationId)
  }

  @Test
  fun `should throw NotFoundException when notification mapping does not exist`() {
    val notificationId = UUID.randomUUID()

    whenever(
      notificationMappingRepository.findByNotificationId(notificationId),
    ).thenReturn(null)

    val exception = assertThrows<NotFoundException> {
      service.getSmsByNotificationId(notificationId)
    }
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

package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.config.NotifyTemplateProperties
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.config.SmsLanguage
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.exception.NotFoundException
import uk.gov.service.notify.NotificationClient
import uk.gov.service.notify.Template
import java.util.UUID

class SmsTemplateResolverServiceTest {

  private val notificationClient: NotificationClient = mock()

  private val notifyTemplateProperties = NotifyTemplateProperties(
    templateIds = mapOf(
      "english-with-appointment-type" to "template-en-with-appointment-type",
      "english-without-appointment-type" to "template-en-without-appointment-type",
      "welsh-with-appointment-type" to "template-cy-with-appointment-type",
      "welsh-without-appointment-type" to "template-cy-without-appointment-type",
    ),
  )
  private val service = SmsTemplateResolverService(
    notifyTemplateProperties = notifyTemplateProperties,
    notificationClient = notificationClient,
  )

  @Test
  fun `should return English template with appointment type`() {
    val templateId = UUID.randomUUID()
    val template =
      Template("""{ "id": $templateId, "name": "test", "type": "sms", "created_at": "2020-01-01T00:00:00Z", "version": 1, "body": "EN" }""")

    whenever(notificationClient.getTemplateById("template-en-with-appointment-type"))
      .thenReturn(template)

    val result = service.getTemplate(
      smsLanguage = SmsLanguage.ENGLISH,
      appointmentTypeCode = "COAP",
    )

    assertEquals(template, result)
  }

  @Test
  fun `should return English template with no appointment type`() {
    val templateId = UUID.randomUUID()
    val template =
      Template("""{ "id": $templateId, "name": "test", "type": "sms", "created_at": "2020-01-01T00:00:00Z", "version": 1, "body": "EN LOCATION" }""")

    whenever(notificationClient.getTemplateById("template-en-without-appointment-type"))
      .thenReturn(template)

    val result = service.getTemplate(
      smsLanguage = SmsLanguage.ENGLISH,
      appointmentTypeCode = "",
    )

    assertEquals(template, result)
  }

@Test
fun `should return Welsh template without appointment type`() {
  val templateId = UUID.randomUUID()
  val template =
    Template(
      """{ "id": "$templateId", "name": "test", "type": "sms", "created_at": "2020-01-01T00:00:00Z", "version": 1, "body": "CY" }""",
    )

  val properties =
    NotifyTemplateProperties(
      templateIds =
        mapOf(
          "welsh-without-appointment-type" to "template-cy-without-appointment-type",
        ),
    )

  val service =
    SmsTemplateResolverService(
      notifyTemplateProperties = properties,
      notificationClient = notificationClient,
    )

  whenever(notificationClient.getTemplateById("template-cy-without-appointment-type"))
    .thenReturn(template)

  val result =
    service.getTemplate(
      smsLanguage = SmsLanguage.WELSH,
      appointmentTypeCode = null,
    )

  assertEquals(template, result)
}

@Test
fun `should throw NotFoundException when English template without appointment type is missing`() {
  val properties =
    NotifyTemplateProperties(
      templateIds = emptyMap(),
    )

  val serviceWithMissingConfig =
    SmsTemplateResolverService(
      notifyTemplateProperties = properties,
      notificationClient = notificationClient,
    )

  val exception =
    assertThrows(NotFoundException::class.java) {
      serviceWithMissingConfig.getTemplate(
        smsLanguage = SmsLanguage.ENGLISH,
        appointmentTypeCode = null,
      )
    }

  assertEquals(
    "No Notify template configured for Language: ENGLISH Variant: WITHOUT_APPOINTMENT_TYPE templateKey: english-without-appointment-type",
    exception.message,
  )
}

@Test
fun `should throw NotFoundException when English template with appointment type is missing`() {
  val properties =
    NotifyTemplateProperties(
      templateIds = emptyMap(),
    )

  val serviceWithMissingConfig =
    SmsTemplateResolverService(
      notifyTemplateProperties = properties,
      notificationClient = notificationClient,
    )

  val exception =
    assertThrows(NotFoundException::class.java) {
      serviceWithMissingConfig.getTemplate(
        smsLanguage = SmsLanguage.ENGLISH,
        appointmentTypeCode = "COAP",
      )
    }

  assertEquals(
    "No Notify template configured for Language: ENGLISH Variant: WITH_APPOINTMENT_TYPE templateKey: english-with-appointment-type",
    exception.message,
  )
}
}

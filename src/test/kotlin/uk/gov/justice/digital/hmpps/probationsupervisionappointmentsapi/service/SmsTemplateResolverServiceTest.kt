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
      "english-with-name-date" to "template-en-no-location",
      "english-with-name-date-location" to "template-en-with-location",
      "welsh-with-name-date" to "template-cy-no-location",
      "welsh-with-name-date-location" to "template-cy-with-location",
    ),
  )
  private val service = SmsTemplateResolverService(
    notifyTemplateProperties = notifyTemplateProperties,
    notificationClient = notificationClient,
  )

  @Test
  fun `should return English template without location`() {
    val templateId = UUID.randomUUID()
    val template =
      Template("""{ "id": $templateId, "name": "test", "type": "sms", "created_at": "2020-01-01T00:00:00Z", "version": 1, "body": "EN" }""")

    whenever(notificationClient.getTemplateById("template-en-no-location"))
      .thenReturn(template)

    val result = service.getTemplate(
      smsLanguage = SmsLanguage.ENGLISH,
      appointmentLocation = null,
    )

    assertEquals(template, result)
  }

  @Test
  fun `should return English template with location`() {
    val templateId = UUID.randomUUID()
    val template =
      Template("""{ "id": $templateId, "name": "test", "type": "sms", "created_at": "2020-01-01T00:00:00Z", "version": 1, "body": "EN LOCATION" }""")

    whenever(notificationClient.getTemplateById("template-en-with-location"))
      .thenReturn(template)

    val result = service.getTemplate(
      smsLanguage = SmsLanguage.ENGLISH,
      appointmentLocation = "Leeds Office",
    )

    assertEquals(template, result)
  }

  @Test
  fun `should return Welsh template without location`() {
    val templateId = UUID.randomUUID()
    val template =
      Template("""{ "id": $templateId, "name": "test", "type": "sms", "created_at": "2020-01-01T00:00:00Z", "version": 1, "body": "CY" }""")

    whenever(notificationClient.getTemplateById("template-cy-no-location"))
      .thenReturn(template)

    val result = service.getTemplate(
      smsLanguage = SmsLanguage.WELSH,
      appointmentLocation = null,
    )

    assertEquals(template, result)
  }

  @Test
  fun `should throw NotFoundException when template is missing`() {
    val properties = NotifyTemplateProperties(
      templateIds = emptyMap(),
    )

    val serviceWithMissingConfig = SmsTemplateResolverService(
      notifyTemplateProperties = properties,
      notificationClient = notificationClient,
    )

    val exception = assertThrows(NotFoundException::class.java) {
      serviceWithMissingConfig.getTemplate(
        smsLanguage = SmsLanguage.ENGLISH,
        appointmentLocation = null,
      )
    }

    assertEquals(
      "No Notify template configured for Language: ENGLISH Variant: WITH_NAME_DATE templateKey: english-with-name-date",
      exception.message,
    )
  }
}

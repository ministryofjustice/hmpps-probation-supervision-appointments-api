package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.config.NotifyTemplateProperties
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.config.SmsLanguage
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.config.TemplateVariant
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.exception.NotFoundException
import uk.gov.service.notify.NotificationClient
import uk.gov.service.notify.Template

@Service
class SmsTemplateResolverService(
  private val notifyTemplateProperties: NotifyTemplateProperties,
  private val notificationClient: NotificationClient,
) {
  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getTemplate(
    smsLanguage: SmsLanguage = SmsLanguage.ENGLISH,
    appointmentLocation: String? = null,
  ): Template? {
    val variant = determineVariant(appointmentLocation)

    val templateKey = "${smsLanguage.name}_${variant.name}"
    val templateId = notifyTemplateProperties.templateIds[templateKey] ?: throw NotFoundException(
      "No Notify template configured for $smsLanguage / $variant",
    )

    try {
      return notificationClient.getTemplateById(templateId)
    } catch (ex: Exception) {
      log.warn("Failed to retrieve template for language $smsLanguage variant $variant templateId $templateId", ex)
      return null
    }
  }

  private fun determineVariant(appointmentLocation: String?): TemplateVariant = if (appointmentLocation.isNullOrBlank()) {
    TemplateVariant.WITH_NAME_DATE
  } else {
    TemplateVariant.WITH_NAME_DATE_LOCATION
  }
}

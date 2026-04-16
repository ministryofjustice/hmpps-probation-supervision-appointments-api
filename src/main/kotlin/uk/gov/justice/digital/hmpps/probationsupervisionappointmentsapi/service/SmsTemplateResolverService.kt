package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

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
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  fun getTemplate(
    smsLanguage: SmsLanguage,
    appointmentLocation: String? = null,
  ): Template {
    val variant =
      if (appointmentLocation.isNullOrBlank()) {
        TemplateVariant.WITH_NAME_DATE
      } else {
        TemplateVariant.WITH_NAME_DATE_LOCATION
      }

    val templateKey = "${smsLanguage.key}-${variant.key}"

    log.info("Getting template: $templateKey")

    val templateId = notifyTemplateProperties.templateIds[templateKey] ?: throw NotFoundException(
      "No Notify template configured for Language: $smsLanguage Variant: $variant templateKey: $templateKey",
    )
    log.info("Template Id fetched : $templateId")

    return notificationClient.getTemplateById(templateId)
  }
}

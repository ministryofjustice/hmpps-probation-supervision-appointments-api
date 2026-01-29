package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

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

  fun getTemplate(
    includeWelshTranslation: Boolean,
    appointmentLocation: String? = null,
  ): Template {
    val variant =
      if (appointmentLocation.isNullOrBlank()) {
        TemplateVariant.WITH_NAME_DATE
      } else {
        TemplateVariant.WITH_NAME_DATE_LOCATION
      }

    val language = if (includeWelshTranslation) SmsLanguage.WELSH else SmsLanguage.ENGLISH
    val templateKey = "${language.name}_${variant.name}"
    val templateId = notifyTemplateProperties.templateIds[templateKey] ?: throw NotFoundException(
      "No Notify template configured for $language / $variant",
    )
    return notificationClient.getTemplateById(templateId)
  }
}

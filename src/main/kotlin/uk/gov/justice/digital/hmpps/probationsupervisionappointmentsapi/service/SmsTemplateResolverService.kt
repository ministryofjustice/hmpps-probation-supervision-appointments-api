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
    smsLanguage: SmsLanguage = SmsLanguage.ENGLISH,
    appointmentLocation: String? = null,
  ): Template {
    val variant =
      if (appointmentLocation.isNullOrBlank()) {
        TemplateVariant.WITH_NAME_DATE
      } else {
        TemplateVariant.WITH_NAME_DATE_LOCATION
      }

    val templateKey = "${smsLanguage.name}_${variant.name}"
    val templateId = notifyTemplateProperties.templateIds[templateKey] ?: throw NotFoundException(
      "No Notify template configured for $smsLanguage / $variant",
    )
    return notificationClient.getTemplateById(templateId)
  }
}

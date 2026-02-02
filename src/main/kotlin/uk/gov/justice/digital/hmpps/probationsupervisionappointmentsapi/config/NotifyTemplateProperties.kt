package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "govuk-notify")
data class NotifyTemplateProperties(
  val templateIds: Map<String, String> = emptyMap(),
)

enum class SmsLanguage {
  ENGLISH,
  WELSH,
}

enum class TemplateVariant {
  WITH_NAME_DATE,
  WITH_NAME_DATE_LOCATION,
}

package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "govuk-notify")
data class NotifyTemplateProperties(
  val templateIds: Map<String, String> = emptyMap(),
) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  init {
    log.info("Loaded notify template IDs: $templateIds")
  }
}

enum class SmsLanguage {
  ENGLISH,
  WELSH,
}

enum class TemplateVariant {
  WITH_NAME_DATE,
  WITH_NAME_DATE_LOCATION,
}

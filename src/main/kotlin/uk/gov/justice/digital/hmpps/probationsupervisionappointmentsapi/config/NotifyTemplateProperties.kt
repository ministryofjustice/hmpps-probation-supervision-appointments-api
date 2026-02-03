package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "govuk-notify")
data class NotifyTemplateProperties(
  val templateIds: Map<String, String>,
) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @PostConstruct
  fun logLoaded() {
    log.info("Loaded notify template IDs: $templateIds")
  }
}

enum class SmsLanguage {
  ENGLISH,
  WELSH,
  ;

  fun key() = name.lowercase()
}

enum class TemplateVariant {
  WITH_NAME_DATE,
  WITH_NAME_DATE_LOCATION,
  ;

  fun key() = name.lowercase().replace('_', '-')
}

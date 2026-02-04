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

enum class SmsLanguage(val key: String) {
  ENGLISH("english"),
  WELSH("welsh"),
}

enum class TemplateVariant(val key: String) {
  WITH_NAME_DATE("with-name-date"),
  WITH_NAME_DATE_LOCATION("with-name-date-location"),
}

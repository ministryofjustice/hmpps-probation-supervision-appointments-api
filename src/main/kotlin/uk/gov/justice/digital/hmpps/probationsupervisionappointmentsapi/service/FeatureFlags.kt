package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import io.flipt.client.FliptClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FeatureFlags(
  private val client: FliptClient?,
) {
  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun enabled(key: String) = try {
    if (client == null) {
      log.warn("Flipt client not configured, all feature flags disabled.")
      false
    } else {
      client
        .evaluateBoolean(key, key, emptyMap<String, String>())
        .isEnabled
    }
  } catch (e: Exception) {
    log.warn("Error retrieving feature flag '$key', defaulting to disabled", e)
    false
  }
}

package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import io.flipt.client.FliptClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FeatureFlagsService(
  private val client: FliptClient,
) {
  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun isEnabled(key: String) = try {
    client
      .evaluateBoolean(key, key, emptyMap<String, String>())
      .isEnabled
  } catch (e: Exception) {
    log.warn("Error retrieving feature flag '$key', defaulting to disabled", e)
    false
  }

  fun isEnabledForUser(key: String, emailAddress: String) = try {
    client.evaluateBoolean(
      key,
      emailAddress,
      mapOf("recipientEmail" to emailAddress.lowercase()),
    ).isEnabled
  } catch (e: Exception) {
    log.warn("Error retrieving feature flag '$key', defaulting to disabled", e)
    false
  }
}

package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import io.flipt.client.FliptClient
import io.flipt.client.models.ClientTokenAuthentication
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.config.FLIPT_NAMESPACE
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.config.FliptConfig

@Service
class FeatureFlagsService(
  private val client: FliptClient,
  private val fliptConfig: FliptConfig
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

  fun getSegments(key: String) {

    var client = FliptClient.builder()
      .url(buildUrl(key))
      .namespace(FLIPT_NAMESPACE)
      .authentication(ClientTokenAuthentication(fliptConfig.token))
      .build()
    client.
  }



  private fun buildUrl(key: String): String {

    return fliptConfig.url + "/api/v1/namespaces/$FLIPT_NAMESPACE/segments/$key"
  }

}

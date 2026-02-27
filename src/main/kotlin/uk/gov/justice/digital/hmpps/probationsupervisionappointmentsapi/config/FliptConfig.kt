package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.config

import io.flipt.client.FliptClient
import io.flipt.client.models.ClientTokenAuthentication
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

const val FLIPT_NAMESPACE = "probation-supervision"

@Configuration
class FliptConfig(
  @Value("\${flipt.url}") val url: String,
  @Value("\${flipt.token}") val token: String,
) {

  @Bean
  fun fliptApiClient(): FliptClient = FliptClient.builder().url(url).namespace(FLIPT_NAMESPACE)
    .authentication(ClientTokenAuthentication(token)).build()
}

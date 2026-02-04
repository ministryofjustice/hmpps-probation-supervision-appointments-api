package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.config

import io.flipt.client.FliptClient
import io.flipt.client.models.ClientTokenAuthentication
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FliptConfig(
  @Value("\${flipt.url}") private val url: String,
  @Value("\${flipt.token}") private val token: String,
) {

  @Bean
  fun fliptApiClient(): FliptClient = FliptClient.builder().url(url).namespace("probation-supervision")
    .authentication(ClientTokenAuthentication(token)).build()
}

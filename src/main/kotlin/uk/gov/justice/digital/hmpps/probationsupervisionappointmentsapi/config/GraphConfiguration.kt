package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.config

import com.azure.identity.ClientSecretCredential
import com.azure.identity.ClientSecretCredentialBuilder
import com.microsoft.graph.serviceclient.GraphServiceClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GraphConfiguration {

  @Value("\${graph-client.client-id}")
  private val clientId: String? = null

  @Value("\${graph-client.client-secret}")
  private val clientSecret: String? = null

  @Value("\${graph-client.tenant-id}")
  private val tenantId: String? = null

  val scope = "https://graph.microsoft.com/.default"

  @Bean
  fun getClientCredentialsToken(): GraphServiceClient {
    val credential: ClientSecretCredential = ClientSecretCredentialBuilder()
      .tenantId(tenantId)
      .clientId(clientId)
      .clientSecret(clientSecret)
      .build()
    return GraphServiceClient(credential, scope)
  }
}

package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.wiremock

import com.microsoft.graph.serviceclient.GraphServiceClient
import com.microsoft.kiota.RequestInformation
import com.microsoft.kiota.authentication.AuthenticationProvider
import okhttp3.OkHttpClient
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class MsGraphTestConfig {

  @Bean
  @Primary
  fun testGraphServiceClient(): GraphServiceClient {
    val auth = object : AuthenticationProvider {
      override fun authenticateRequest(
        request: RequestInformation,
        additionalAuthenticationContext: MutableMap<String, Any>?,
      ) {
        request.headers.add("Authorization", "Bearer dummy-token")
      }
    }

    val ok = OkHttpClient.Builder()
      .addInterceptor { chain ->
        val original = chain.request()
        val newUrl = original.url.newBuilder()
          .scheme("http")
          .host("localhost")
          .port(8091)
          .build()
        chain.proceed(original.newBuilder().url(newUrl).build())
      }
      .build()

    return GraphServiceClient(auth, ok)
  }
}

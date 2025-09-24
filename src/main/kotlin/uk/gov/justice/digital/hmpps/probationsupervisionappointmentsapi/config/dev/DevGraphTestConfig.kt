package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.config.dev

import com.microsoft.graph.serviceclient.GraphServiceClient
import com.microsoft.kiota.authentication.AuthenticationProvider
import okhttp3.OkHttpClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile

@Configuration
@Profile("h2-mem")
class DevGraphTestConfig {

  @Bean
  @Primary
  fun testGraphServiceClient(): GraphServiceClient {
    val auth =
      AuthenticationProvider { request, additionalAuthenticationContext -> request.headers.add("Authorization", "Bearer dummy-token") }

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

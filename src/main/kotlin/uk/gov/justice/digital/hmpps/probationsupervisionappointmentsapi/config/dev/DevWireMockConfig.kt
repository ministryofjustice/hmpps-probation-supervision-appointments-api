package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.config.dev

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.nio.file.Paths

@Configuration
@Profile("h2-mem")
class DevWireMockConfig {

  @Bean(initMethod = "start", destroyMethod = "stop")
  fun wireMockServer(): WireMockServer {
    val projectRoot = Paths.get(System.getProperty("user.dir")).normalize()
    val stubsPath = projectRoot.resolve("src/wiremock-stubs").normalize()

    println(">>> WireMock: loading stubs from $stubsPath")

    return WireMockServer(
      WireMockConfiguration.options()
        .port(8091)
        .usingFilesUnderDirectory(stubsPath.toFile().absolutePath)
        .templatingEnabled(true),
    )
  }
}

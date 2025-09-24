package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.config.dev

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.nio.file.Paths

@Configuration
@Profile("h2-mem")
@EnableConfigurationProperties(WireMockProps::class)
class DevWireMockConfig(private val props: WireMockProps) {

  @Bean(initMethod = "start", destroyMethod = "stop")
  fun wireMockServer(): WireMockServer {
    val projectRoot = Paths.get(System.getProperty("user.dir")).normalize()
    val stubsPath = projectRoot.resolve("src/wiremock-stubs").normalize()

    println(">>> WireMock: loading stubs from $stubsPath")

    return WireMockServer(
      WireMockConfiguration.options()
        .port(props.port)
        .usingFilesUnderDirectory(stubsPath.toFile().absolutePath)
        .templatingEnabled(true),
    )
  }
}

@ConfigurationProperties(prefix = "wiremock")
data class WireMockProps(
  var port: Int
)

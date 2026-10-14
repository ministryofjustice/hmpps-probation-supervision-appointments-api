package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.events

import org.slf4j.LoggerFactory
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

object LocalStackContainer {
  private val log = LoggerFactory.getLogger(this::class.java)
  val instance by lazy { startLocalstackIfNotRunning() }

  private fun startLocalstackIfNotRunning(): LocalStackContainer? {
    if (isLocalStackRunning()) {
      return null
    }

    val logConsumer = Slf4jLogConsumer(log).withPrefix("localstack")

    return LocalStackContainer(
      // community-archive, not a versioned/`latest` tag: current LocalStack images are the licensed
      // Pro build and require a LOCALSTACK_AUTH_TOKEN to start, which would break SQS/SNS here.
      DockerImageName.parse("localstack/localstack").withTag("community-archive"),
    ).apply {
      withServices("sns", "sqs")
      withEnv("HOSTNAME_EXTERNAL", "localhost")
      withEnv("DEFAULT_REGION", "eu-west-2")
      waitingFor(Wait.forLogMessage(".*Ready\\.\n", 1))
      start()
      followOutput(logConsumer)
    }
  }

  private fun isLocalStackRunning(): Boolean = try {
    val client = HttpClient.newHttpClient()

    val request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:4566/_localstack/health"))
      .GET()
      .build()

    val response = client.send(request, HttpResponse.BodyHandlers.ofString())

    response.statusCode() == 200
  } catch (e: Exception) {
    false
  }
}

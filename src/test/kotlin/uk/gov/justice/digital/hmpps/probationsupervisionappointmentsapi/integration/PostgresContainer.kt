package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration

import org.slf4j.LoggerFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.utility.DockerImageName

object PostgresContainer {
  private val log = LoggerFactory.getLogger(this::class.java)

  val instance: PostgreSQLContainer<*> by lazy { start() }

  private fun start(): PostgreSQLContainer<*> {
    val logConsumer = Slf4jLogConsumer(log).withPrefix("postgres")

    return PostgreSQLContainer(DockerImageName.parse("postgres:16.15")).apply {
      withDatabaseName("probation_supervision_appointments")
      withUsername("test")
      withPassword("test")
      start()
      followOutput(logConsumer)
    }
  }
}

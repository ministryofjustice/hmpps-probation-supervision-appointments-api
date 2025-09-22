package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@SpringBootApplication
@EnableJpaAuditing(modifyOnCreate = true)
class ProbationSupervisionAppointmentsApi

fun main(args: Array<String>) {
  runApplication<ProbationSupervisionAppointmentsApi>(*args)
}

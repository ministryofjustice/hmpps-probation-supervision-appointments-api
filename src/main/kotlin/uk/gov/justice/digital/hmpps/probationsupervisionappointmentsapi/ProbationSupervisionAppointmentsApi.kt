package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ProbationSupervisionAppointmentsApi

fun main(args: Array<String>) {
  runApplication<ProbationSupervisionAppointmentsApi>(*args)
}

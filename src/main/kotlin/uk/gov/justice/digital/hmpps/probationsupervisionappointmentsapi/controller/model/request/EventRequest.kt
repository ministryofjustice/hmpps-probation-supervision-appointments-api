package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request

import java.time.LocalDateTime

data class EventRequest(
  val fromEmail: String,
  val recipients: List<Recipient>,
  val message: String,
  val subject: String,
  val start: LocalDateTime,
  val duration: Long,
  val supervisionAppointmentUrn: String,
)

data class Recipient(
  val emailAddress: String,
  val name: String,
)

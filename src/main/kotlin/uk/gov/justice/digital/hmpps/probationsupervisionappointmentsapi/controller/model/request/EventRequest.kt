package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request

import java.time.ZonedDateTime

data class EventRequest(
  val recipients: List<Recipient>,
  val message: String,
  val subject: String,
  val start: ZonedDateTime,
  val durationInMinutes: Long,
  val supervisionAppointmentUrn: String,
)

data class Recipient(
  val emailAddress: String,
  val name: String,
)

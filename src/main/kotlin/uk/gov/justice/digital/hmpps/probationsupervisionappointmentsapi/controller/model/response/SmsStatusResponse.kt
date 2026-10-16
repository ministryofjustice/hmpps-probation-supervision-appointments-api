package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response

import java.time.Instant
import java.util.UUID

data class SmsStatusResponse(
  val notificationId: UUID,
  val sentAt: Instant,
  val status: String?,
  val statusUpdatedAt: Instant?,
)

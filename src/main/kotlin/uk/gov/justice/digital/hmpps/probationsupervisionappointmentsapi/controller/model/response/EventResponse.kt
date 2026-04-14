package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response

import java.util.UUID

data class EventResponse(
  val id: String?,
  val subject: String?,
  val startDate: String?,
  val endDate: String?,
  val attendees: List<String>? = emptyList(),
  val smsResponse: SmsResponse? = null,
)

data class SmsResponse(
  val englishNotificationId: UUID,
  val welshNotificationId: UUID?,
)

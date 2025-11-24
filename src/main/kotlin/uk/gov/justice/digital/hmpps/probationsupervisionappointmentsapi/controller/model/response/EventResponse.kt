package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response

data class EventResponse(
  val id: String?,
  val subject: String?,
  val startDate: String?,
  val endDate: String?,
  val attendees: List<String>? = emptyList(),
)

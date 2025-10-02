package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response

data class DeliusOutlookMappingsResponse(
  val supervisionAppointmentUrn: String,
  val outlookId: String,
  val createdAt: String,
  val updatedAt: String,
)

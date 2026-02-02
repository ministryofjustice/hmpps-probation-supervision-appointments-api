package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response

data class SmsPreviewResponse(
  val englishSmsPreview: String,
  val welshSmsPreview: String? = null,
)

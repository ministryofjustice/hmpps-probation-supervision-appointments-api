package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response

data class SmsNotificationResponse(
  val smsMessage: String,
  val deliusExternalReference: String,
)

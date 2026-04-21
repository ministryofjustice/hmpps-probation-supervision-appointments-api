package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request

data class FeatureSwitchEnabledForUserRequest(
  val email: String,
  val featureSwitchName: String,
)

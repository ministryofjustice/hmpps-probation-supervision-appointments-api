package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.FeatureSwitchEnabledForUserRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.FeatureSwitchResponse
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.FeatureFlagsService

@RestController
@Tag(name = "Feature switch", description = "Feature switch service to check if a feature is enabled/disabled")
@PreAuthorize("hasRole('ROLE_PROBATION_API__PROBATION_SUPERVISION_APPOINTMENTS__EVENTS')")
@RequestMapping("/feature-switch")
class FeatureSwitchController(val featureSwitchService: FeatureFlagsService) {

  @PostMapping("/isFeatureEnabledForUser")
  @ResponseStatus(HttpStatus.OK)
  fun isFeatureEnabledForAUser(@RequestBody featureSwitchEnabledForUserRequest: FeatureSwitchEnabledForUserRequest) = FeatureSwitchResponse(featureSwitchService.isEnabledForUser(featureSwitchEnabledForUserRequest.featureSwitchName, featureSwitchEnabledForUserRequest.email))
}

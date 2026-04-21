package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.FeatureSwitchResponse
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.FeatureFlagsService

@RestController
@Tag(name = "Feature switch", description = "Feature switch service to check if a feature is enabled/disabled")
@PreAuthorize("hasRole('ROLE_PROBATION_API__PROBATION_SUPERVISION_APPOINTMENTS__EVENTS')")
@RequestMapping("/feature-switch")
class FeatureSwitchController(val featureSwitchService: FeatureFlagsService) {

  @RequestMapping(
    method = [RequestMethod.GET],
    value = ["/isFeatureEnabledForUser"],
    produces = ["application/json"],
  )
  fun isFeatureEnabledForAUser(
    @Parameter(description = "The persons email address") @RequestParam(
      value = "email",
      required = true,
    ) email: String,
    @Parameter(description = "Name of the feature switch") @RequestParam(
      value = "featureSwitchName",
      required = true,
    ) featureSwitchName: String,
  ) = FeatureSwitchResponse(featureSwitchService.isEnabledForUser(featureSwitchName, email))
}

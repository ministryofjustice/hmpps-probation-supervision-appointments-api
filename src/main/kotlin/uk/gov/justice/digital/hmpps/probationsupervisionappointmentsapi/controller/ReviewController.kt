package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "AAA", description = "Outlook Calendar API")
@RequestMapping("/review")
@PreAuthorize("hasRole('ROLE_PROBATION_API__PROBATION_SUPERVISION_APPOINTMENTS__EVENTS')")
class ReviewController {

  @GetMapping("/event")
  @ResponseStatus(HttpStatus.OK)
  fun getOutlookEvent(
    @RequestParam supervisionAppointmentUrn: String,
  ) = "hello"
}

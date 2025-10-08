package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.UserService

@RestController
@Tag(name = "Outlook Users", description = "Outlook Users API")
@RequestMapping("/users")
@PreAuthorize("hasRole('ROLE_PROBATION_API__PROBATION_SUPERVISION_APPOINTMENTS__EVENTS')")
class OutlookUserController(val userService: UserService) {

  @GetMapping("/search")
  fun getUsers(@RequestParam(required = false) query: String?) = userService.getUsers(query)

  @GetMapping("/count")
  fun countNumberOfUser() = userService.countNumberOfUser()
}

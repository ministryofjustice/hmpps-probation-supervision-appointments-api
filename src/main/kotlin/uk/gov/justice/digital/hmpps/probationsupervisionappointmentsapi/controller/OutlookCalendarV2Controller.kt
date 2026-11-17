package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.EventRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.CalendarService

@RestController
@Tag(name = "Outlook Calendar", description = "Outlook Calendar API")
@RequestMapping("/v2/calendar")
@PreAuthorize("hasRole('ROLE_PROBATION_API__PROBATION_SUPERVISION_APPOINTMENTS__EVENTS')")
class OutlookCalendarV2Controller(private val calendarService: CalendarService) {

  @PostMapping("/event")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Record appointment SMS notifications",
    description = "Sends and records eligible appointment SMS notifications without creating an Outlook calendar event.",
  )
  fun saveEvent(@RequestBody event: EventRequest) = calendarService.sendEventV2(event)
}

package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.EventRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.DeliusOutlookMappingsResponse
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.CalendarService

@RestController
@Tag(name = "Outlook Calendar", description = "Outlook Calendar API")
@RequestMapping("/calendar")
@PreAuthorize("hasRole('ROLE_PROBATION_API__PROBATION_SUPERVISION_APPOINTMENTS__EVENTS')")
class OutlookCalendarController(val calendarService: CalendarService) {

  @PostMapping("/event")
  @ResponseStatus(HttpStatus.CREATED)
  fun saveEvent(@RequestBody event: EventRequest) = calendarService.sendEvent(event)

  @GetMapping("/event")
  @ResponseStatus(HttpStatus.OK)
  fun getEventDetails(
    @RequestParam supervisionAppointmentUrn: String,
  ): DeliusOutlookMappingsResponse = calendarService.getEventDetailsMappings(supervisionAppointmentUrn)
}

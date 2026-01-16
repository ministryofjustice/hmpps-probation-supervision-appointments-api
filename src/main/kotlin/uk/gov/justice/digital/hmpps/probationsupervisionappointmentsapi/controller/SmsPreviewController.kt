package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.SmsPreviewRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.SmsPreviewResponse
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.SmsPreviewService

@RestController
@Tag(name = "SMS", description = "Sms Preview API")
@PreAuthorize("hasRole('ROLE_PROBATION_API__PROBATION_SUPERVISION_APPOINTMENTS__EVENTS')")
@RequestMapping("/sms")
class SmsPreviewController(val smsPreviewService: SmsPreviewService) {

  @PostMapping(value = ["/preview"])
  fun previewSms(
    @RequestBody request: SmsPreviewRequest,
  ): ResponseEntity<SmsPreviewResponse> = ResponseEntity.ok(smsPreviewService.generatePreview(request))
}

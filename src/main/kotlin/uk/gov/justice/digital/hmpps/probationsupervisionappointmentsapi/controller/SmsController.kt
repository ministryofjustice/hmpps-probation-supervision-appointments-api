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
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.SmsPreviewRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.SmsService
import java.util.UUID

@RestController
@Tag(name = "SMS", description = "Sms Preview API")
@PreAuthorize("hasRole('ROLE_PROBATION_API__PROBATION_SUPERVISION_APPOINTMENTS__EVENTS')")
@RequestMapping("/sms")
class SmsController(val smsService: SmsService) {

  @PostMapping(value = ["/preview"])
  fun previewSms(
    @RequestBody request: SmsPreviewRequest,
  ) = smsService.generatePreview(request)

  @GetMapping("/message")
  @ResponseStatus(HttpStatus.OK)
  fun getSmsMessageByNotificationId(
    @RequestParam notificationId: UUID,
  ) = smsService.getSmsByNotificationId(notificationId)
}

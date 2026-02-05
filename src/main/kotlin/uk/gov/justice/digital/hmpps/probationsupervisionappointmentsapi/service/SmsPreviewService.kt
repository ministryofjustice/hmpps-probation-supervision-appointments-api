package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.config.SmsLanguage
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.AppointmentType
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.SmsPreviewRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.SmsPreviewResponse
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.SmsUtil.Companion.APPOINTMENT_DATE
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.SmsUtil.Companion.APPOINTMENT_LOCATION
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.SmsUtil.Companion.APPOINTMENT_TIME
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.SmsUtil.Companion.APPOINTMENT_TYPE
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.SmsUtil.Companion.FIRST_NAME
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.util.EnglishToWelshTranslator
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Service
class SmsPreviewService(
  private val smsTemplateResolverService: SmsTemplateResolverService,
) {

  fun generatePreview(request: SmsPreviewRequest) = SmsPreviewResponse(
    englishSmsPreview = buildPreview(request, SmsLanguage.ENGLISH),
    welshSmsPreview = if (request.includeWelshPreview) buildPreview(request, SmsLanguage.WELSH) else null,
  )

  private fun buildPreview(
    request: SmsPreviewRequest,
    smsLanguage: SmsLanguage,
  ): String {
    val template = smsTemplateResolverService.getTemplate(smsLanguage, request.appointmentLocation)

    // Base (English) values
    val englishDate = request.dateAndTimeOfAppointment.toNotifyDate()
    val englishTime = request.dateAndTimeOfAppointment.toNotifyTime()

    // Translate only if Welsh
    val date = if (smsLanguage == SmsLanguage.WELSH) {
      englishDate
        .split(" ")
        .joinToString(" ") { EnglishToWelshTranslator.toWelsh(it) }
    } else {
      englishDate
    }

    val personalisation = mapOf(
      FIRST_NAME to request.firstName,
      APPOINTMENT_DATE to date,
      APPOINTMENT_TIME to englishTime,
      APPOINTMENT_LOCATION to request.appointmentLocation.orEmpty(),
      APPOINTMENT_TYPE to getAppointmentType(request.appointmentTypeCode, smsLanguage),
    )

    return substitute(template.body, personalisation)
  }

  private fun getAppointmentType(appointmentTypeCode: String?, smsLanguage: SmsLanguage): String {
    val type = AppointmentType.fromCode(appointmentTypeCode)
    return (if (smsLanguage == SmsLanguage.WELSH) type?.welsh else type?.english).orEmpty()
  }

  /**
   * Simple placeholder replacement for preview only
   * ((FirstName)), ((Date)), ((Location))
   */
  private fun substitute(
    template: String,
    values: Map<String, String>,
  ): String = values.entries.fold(template) { acc, (key, value) ->
    acc.replace("(($key))", value)
  }
}

private val DATE_FORMATTER =
  DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)

private val TIME_FORMATTER =
  DateTimeFormatter.ofPattern("ha", Locale.UK)

fun ZonedDateTime.toNotifyDate(): String = this.format(DATE_FORMATTER)

fun ZonedDateTime.toNotifyTime(): String = this.format(TIME_FORMATTER)
  .lowercase()

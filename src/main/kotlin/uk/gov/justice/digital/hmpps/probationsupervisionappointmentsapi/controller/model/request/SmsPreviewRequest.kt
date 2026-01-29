package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request

import java.time.ZonedDateTime

data class SmsPreviewRequest(
  val firstName: String,
  val dateAndTimeOfAppointment: ZonedDateTime,
  val appointmentLocation: String? = null,
  val appointmentTypeCode: String?,
  val includeWelshPreview: Boolean = false,
)

enum class AppointmentType(val code: String, val english: String, val welsh: String) {
  PlannedOfficeVisitNS("COAP", "office visit", "ymweliad swyddfa"),
  PlannedTelephoneContactNS("COPT", "telephone appointment", "apwyntiad dros y ffôn"),
  PlannedVideoContactNS("COVC", "video link appointment", "apwyntiad dolen fideo"),
  PannedContactOtherThanOffice("COOO", "appointment", "apwyntiad"),
  InitialAppointmentInOfficeNS("COAI", "office visit", "ymweliad swyddfa"),
  HomeVisitToCaseNS("CHVS", "home visit", "ymweliad cartref"),
  ThreeWayMeetingNS("C084", "appointment", "apwyntiad"),
  PlannedDoorstepContactNS("CODC", "doorstep visit", "ymweliad carreg y drws"),
  InterviewForReportOther("COSR", "appointment", "apwyntiad"),
  ;

  companion object {
    fun fromCode(code: String?): AppointmentType? = entries.find { it.code == code }
  }
}

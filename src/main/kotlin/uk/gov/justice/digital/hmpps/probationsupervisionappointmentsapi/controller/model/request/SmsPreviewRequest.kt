package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request

import java.time.ZonedDateTime

data class SmsPreviewRequest(
  val firstName: String,
  val dateAndTimeOfAppointment: ZonedDateTime,
  val appointmentLocation: String? = null,
  val appointmentTypeCode: String?,
  val includeWelshPreview: Boolean = false,
  val practitionerFirstName: String? = null,
)

enum class AppointmentType(val code: String, val english: String, val welsh: String) {
  PlannedOfficeVisitNS("COAP", "an office appointment", "apwyntiad yn y swyddfa"),
  PlannedTelephoneContactNS("COPT", "a telephone appointment", "apwyntiad dros y ffôn"),
  PlannedVideoContactNS("COVC", "a video appointment", "apwyntiad fideo"),
  PannedContactOtherThanOffice("COOO", "an appointment", "apwyntiad"),
  InitialAppointmentInOfficeNS("COAI", "an office appointment", "ymweliad swyddfa"),
  HomeVisitToCaseNS("CHVS", "a home visit", "ymweliad cartref"),
  ThreeWayMeetingNS("C084", "an appointment", "apwyntiad"),
  PlannedDoorstepContactNS("CODC", "a doorstep visit", "ymweliad carreg y drws"),
  InterviewForReportOther("COSR", "an appointment", "apwyntiad"),
  ;

  companion object {
    fun fromCode(code: String?): AppointmentType? = entries.find { it.code == code }
  }
}

package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request

import java.time.ZonedDateTime

data class SmsPreviewRequest(
  val firstName: String,
  val dateAndTimeOfAppointment: ZonedDateTime,
  val appointmentLocation: String? = null,
  val appointmentTypeCode: String?,
  val includeWelshPreview: Boolean = false,
  val practitionerFirstName: String? = null,
  val useNewSmsAppointmentTemplate: Boolean = false,
)

enum class AppointmentType(
  val code: String,
  val english: String,
  val welsh: String,
  val legacyEnglish: String,
  val legacyWelsh: String,
) {
  PlannedOfficeVisitNS(
    "COAP",
    "an office appointment",
    "apwyntiad yn y swyddfa",
    "office visit",
    "ymweliad swyddfa",
  ),
  PlannedTelephoneContactNS(
    "COPT",
    "a telephone appointment",
    "apwyntiad dros y ffôn",
    "telephone appointment",
    "apwyntiad dros y ffôn",
  ),
  PlannedVideoContactNS(
    "COVC",
    "a video appointment",
    "apwyntiad fideo",
    "video link appointment",
    "apwyntiad dolen fideo",
  ),
  PannedContactOtherThanOffice(
    "COOO",
    "an appointment",
    "apwyntiad",
    "appointment",
    "apwyntiad",
  ),
  InitialAppointmentInOfficeNS(
    "COAI",
    "an office appointment",
    "ymweliad swyddfa",
    "office visit",
    "ymweliad swyddfa",
  ),
  HomeVisitToCaseNS(
    "CHVS",
    "a home visit",
    "ymweliad cartref",
    "home visit",
    "ymweliad cartref",
  ),
  ThreeWayMeetingNS(
    "C084",
    "an appointment",
    "apwyntiad",
    "appointment",
    "apwyntiad",
  ),
  PlannedDoorstepContactNS(
    "CODC",
    "a doorstep visit",
    "ymweliad carreg y drws",
    "doorstep visit",
    "ymweliad carreg y drws",
  ),
  InterviewForReportOther(
    "COSR",
    "an appointment",
    "apwyntiad",
    "appointment",
    "apwyntiad",
  ),
  ;

  companion object {
    fun fromCode(code: String?): AppointmentType? = entries.find { it.code == code }
  }
}

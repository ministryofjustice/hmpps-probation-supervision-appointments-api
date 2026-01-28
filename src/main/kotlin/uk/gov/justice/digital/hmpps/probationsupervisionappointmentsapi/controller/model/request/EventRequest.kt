package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request

import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.config.SmsLanguage
import java.time.ZonedDateTime

data class EventRequest(
  val recipients: List<Recipient>,
  val message: String,
  val subject: String,
  val start: ZonedDateTime,
  val durationInMinutes: Long,
  val supervisionAppointmentUrn: String,
  val smsEventRequest: SmsEventRequest? = null,
)

data class Recipient(
  val emailAddress: String,
  val name: String,
)

data class SmsEventRequest(
  val firstName: String,
  val mobileNumber: String?,
  val crn: String,
  val smsOptIn: Boolean,
  val smsLanguage: SmsLanguage = SmsLanguage.ENGLISH,
  val appointmentLocation: String? = null,
  val appointmentTypeCode: String? = null,
)
data class RescheduleEventRequest(
  val rescheduledEventRequest: EventRequest,
  val oldSupervisionAppointmentUrn: String,
)

package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import io.sentry.Sentry
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.config.SmsLanguage
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.AppointmentType
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.EventRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.SmsEventRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.EventResponse
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.SmsResponse
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.NotificationMapping
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.NotificationMappingRepository
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.SmsUtil.Companion.APPOINTMENT_DATE
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.SmsUtil.Companion.APPOINTMENT_TIME
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.SmsUtil.Companion.APPOINTMENT_TYPE
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.SmsUtil.Companion.FIRST_NAME
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.SmsUtil.Companion.PRACTITIONER_FIRST_NAME
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.util.EnglishToWelshTranslator
import uk.gov.service.notify.NotificationClient
import uk.gov.service.notify.SendSmsResponse
import java.time.ZonedDateTime

@Service
class CalendarV2Service(
  private val notificationMappingRepository: NotificationMappingRepository,
  private val notificationClient: NotificationClient,
  private val telemetryService: TelemetryService,
  private val templateResolverService: SmsTemplateResolverService,
) {
  fun sendEvent(eventRequest: EventRequest): EventResponse {
    val response = EventResponse(
      id = null,
      subject = eventRequest.subject,
      startDate = eventRequest.start.toString(),
      endDate = eventRequest.start.plusMinutes(eventRequest.durationInMinutes).toString(),
      attendees = eventRequest.recipients.map { it.emailAddress },
    )

    if (eventRequest.start.isBefore(ZonedDateTime.now())) {
      telemetryService.trackEvent(
        "AppointmentInThePast",
        mapOf("supervisionAppointmentUrn" to eventRequest.supervisionAppointmentUrn),
      )
      return response
    }

    val smsResponse = sendSMSNotification(eventRequest)
    return response.copy(smsResponse = smsResponse)
  }

  fun sendSMSNotification(eventRequest: EventRequest): SmsResponse? {
    val smsRequest = eventRequest.smsEventRequest ?: return null
    if (!smsRequest.smsOptIn) return null

    val sendSmsEnglishResponse = sendSms(
      eventRequest = eventRequest,
      templateValues = buildTemplateValues(eventRequest, smsRequest, SmsLanguage.ENGLISH),
      smsLanguage = SmsLanguage.ENGLISH,
    )
    val sendSmsWelshResponse = if (smsRequest.includeWelshTranslation) {
      sendSms(
        eventRequest = eventRequest,
        templateValues = buildTemplateValues(eventRequest, smsRequest, SmsLanguage.WELSH),
        smsLanguage = SmsLanguage.WELSH,
      )
    } else {
      null
    }

    return SmsResponse(
      sendSmsEnglishResponse?.notificationId,
      sendSmsWelshResponse?.notificationId,
    )
  }

  private fun buildTemplateValues(
    eventRequest: EventRequest,
    smsRequest: SmsEventRequest,
    smsLanguage: SmsLanguage,
  ): Map<String, String> {
    val englishDate = eventRequest.start.toNotifyDate()
    val date = if (smsLanguage == SmsLanguage.WELSH) {
      englishDate.split(" ").joinToString(" ") { EnglishToWelshTranslator.toWelsh(it) }
    } else {
      englishDate
    }
    val appointmentType = AppointmentType.fromCode(smsRequest.appointmentTypeCode)

    return mapOf(
      FIRST_NAME to smsRequest.firstName,
      PRACTITIONER_FIRST_NAME to smsRequest.practitionerFirstName.orEmpty(),
      APPOINTMENT_DATE to date,
      APPOINTMENT_TIME to eventRequest.start.toNotifyTime(),
      APPOINTMENT_TYPE to if (smsLanguage == SmsLanguage.WELSH) appointmentType?.welsh.orEmpty() else appointmentType?.english.orEmpty(),
    )
  }

  private fun sendSms(
    eventRequest: EventRequest,
    templateValues: Map<String, String>,
    smsLanguage: SmsLanguage,
  ): SendSmsResponse? {
    val smsRequest = requireNotNull(eventRequest.smsEventRequest)
    val telemetryProperties = mapOf(
      "crn" to smsRequest.crn,
      "supervisionAppointmentUrn" to eventRequest.supervisionAppointmentUrn,
      "smsLanguage" to smsLanguage.name,
    )

    try {
      val template = templateResolverService.getNewAppointmentTemplate(smsLanguage, smsRequest.appointmentTypeCode)
      val smsResponse = notificationClient.sendSms(
        template.id.toString(),
        smsRequest.mobileNumber,
        templateValues,
        smsRequest.crn,
      )
      notificationMappingRepository.save(
        NotificationMapping(
          deliusExternalReference = eventRequest.supervisionAppointmentUrn,
          notificationId = smsResponse.notificationId,
          templateId = smsResponse.templateId,
          message = smsResponse.body,
          crn = smsRequest.crn,
        ),
      )
      telemetryService.trackEvent("AppointmentReminderSent", telemetryProperties)
      return smsResponse
    } catch (e: Exception) {
      Sentry.captureException(e)
      return null
    }
  }
}

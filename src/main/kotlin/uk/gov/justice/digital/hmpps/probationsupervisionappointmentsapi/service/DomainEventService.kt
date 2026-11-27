package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.EventRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message.DomainEventPublisher
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message.PersonReference
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.toJavaInstant

@Service
class DomainEventService(
  @Value("\${services.supervision-api.base-url}") private val supervisionApiBaseUrl: String,
  private val domainEventPublisher: DomainEventPublisher,
) {

  private fun buildContactEvent(crn: String, notificationId: UUID) = HmppsDomainEvent(
    eventType = "probation.appointment.sms-sent-to-pop",
    description = "An sms has been sent, please create a contact",
    version = 1,
    detailUrl = "$supervisionApiBaseUrl/sms/message?notificationId=$notificationId",
    personReference = forCrn(crn),
    additionalInformation = mapOf(
      "applicationId" to UUID.randomUUID().toString(),
    ),
    occurredAt = ZonedDateTime.ofInstant(
      Clock.System.now().toJavaInstant(),
      ZoneId.of("Europe/London"),
    ),
  )

  private fun buildOutlookCalendarRequestEvent(eventRequest: EventRequest) = HmppsDomainEvent(
    eventType = "probation.appointment.outlook-calendar-requested",
    description = "An Outlook calendar appointment has been requested",
    version = 1,
    detailUrl = null,
    personReference = eventRequest.smsEventRequest?.crn?.let { forCrn(it) },
    additionalInformation = mapOf(
      "supervisionAppointmentUrn" to eventRequest.supervisionAppointmentUrn,
      "recipients" to eventRequest.recipients,
      "subject" to eventRequest.subject,
      "message" to eventRequest.message,
      "start" to eventRequest.start.toString(),
      "durationInMinutes" to eventRequest.durationInMinutes,
    ),
    occurredAt = ZonedDateTime.ofInstant(
      Clock.System.now().toJavaInstant(),
      ZoneId.of("Europe/London"),
    ),
  )

  private fun forCrn(crn: String) = PersonReference(listOf(PersonReference.PersonIdentifier("CRN", crn)))
  fun buildAndPublishContactEvent(crn: String, notificationId: UUID) {
    val contactEvent = buildContactEvent(crn, notificationId)
    domainEventPublisher.publish(contactEvent)
  }

  fun buildAndPublishOutlookCalendarRequest(eventRequest: EventRequest) {
    val outlookRequestEvent = buildOutlookCalendarRequestEvent(eventRequest)
    domainEventPublisher.publish(outlookRequestEvent, "outlookCalendarRequestEventSent")
  }
}

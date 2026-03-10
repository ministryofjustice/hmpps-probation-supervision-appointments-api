package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
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
    version = HmppsDomainEvent.DOMAIN_EVENT_VERSION,
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

  private fun forCrn(crn: String) = PersonReference(listOf(PersonReference.PersonIdentifier("CRN", crn)))
  fun buildAndPublishContactEvent(crn: String, notificationId: UUID) {
    val buildContactEvent = buildContactEvent(crn, notificationId)
    domainEventPublisher.publish(buildContactEvent)
  }
}

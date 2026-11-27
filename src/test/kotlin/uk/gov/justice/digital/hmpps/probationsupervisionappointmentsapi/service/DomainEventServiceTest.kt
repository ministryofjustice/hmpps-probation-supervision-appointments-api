package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.EventRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.Recipient
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.SmsEventRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message.DomainEventPublisher
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message.PersonReference
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class DomainEventServiceTest {

  @Mock
  private lateinit var domainEventPublisher: DomainEventPublisher

  private lateinit var service: DomainEventService

  private val supervisionApiBaseUrl = "http://supervision-api"

  private val recipient = Recipient("pop@example.com", "Pop Name")

  private val smsEventRequest = SmsEventRequest(
    firstName = "Alex",
    mobileNumber = "07700900000",
    crn = "X12345",
    smsOptIn = true,
    practitionerFirstName = "Sam",
  )
  private val eventRequest = EventRequest(
    recipients = listOf(recipient),
    message = "Test Message Body",
    subject = "Test Subject",
    start = ZonedDateTime.parse("2050-01-01T10:00:00Z"),
    durationInMinutes = 45,
    supervisionAppointmentUrn = "urn:test:123",
    smsEventRequest = smsEventRequest,
  )

  @BeforeEach
  fun setup() {
    service = DomainEventService(
      supervisionApiBaseUrl = supervisionApiBaseUrl,
      domainEventPublisher = domainEventPublisher,
    )
  }

  @Test
  fun `should build and publish contact event`() {
    val crn = "X12345"
    val notificationId = UUID.randomUUID()

    service.buildAndPublishContactEvent(crn, notificationId)

    val eventCaptor = argumentCaptor<HmppsDomainEvent>()

    verify(domainEventPublisher).publish(eventCaptor.capture(), eq("smsContactEventSent"))
    val event = eventCaptor.firstValue

    assertEquals("probation.appointment.sms-sent-to-pop", event.eventType)
    assertEquals("An sms has been sent, please create a contact", event.description)
    assertEquals(1, event.version)

    assertEquals(
      "$supervisionApiBaseUrl/sms/message?notificationId=$notificationId",
      event.detailUrl,
    )

    assertEquals("CRN", event.personReference?.identifiers?.first()?.type)
    assertEquals(crn, event.personReference?.identifiers?.first()?.value)

    assertTrue(event.additionalInformation.containsKey("applicationId"))

    assertNotNull(event.occurredAt)
  }

  @Test
  fun `should build and publish outlook calendar request event`() {
    service.buildAndPublishOutlookCalendarRequest(eventRequest)

    val eventCaptor = argumentCaptor<HmppsDomainEvent>()
    verify(domainEventPublisher).publish(eventCaptor.capture(), eq("outlookCalendarRequestEventSent"))

    val event = eventCaptor.firstValue
    assertEquals("probation.appointment.outlook-calendar-requested", event.eventType)
    assertEquals("An Outlook calendar appointment has been requested", event.description)
    assertEquals(1, event.version)
    assertNull(event.detailUrl)
    assertEquals(listOf(PersonReference.PersonIdentifier("CRN", "X12345")), event.personReference?.identifiers)
    assertEquals(
      mapOf(
        "supervisionAppointmentUrn" to eventRequest.supervisionAppointmentUrn,
        "recipients" to eventRequest.recipients,
        "subject" to eventRequest.subject,
        "message" to eventRequest.message,
        "start" to eventRequest.start.toString(),
        "durationInMinutes" to eventRequest.durationInMinutes,
      ),
      event.additionalInformation,
    )
    assertNotNull(event.occurredAt)
    assertEquals(ZoneId.of("Europe/London"), event.occurredAt.zone)
  }

  @Test
  fun `should set personReference to null when SMS details are absent`() {
    val requestWithoutSms = eventRequest.copy(smsEventRequest = null)

    service.buildAndPublishOutlookCalendarRequest(requestWithoutSms)

    val eventCaptor = argumentCaptor<HmppsDomainEvent>()
    verify(domainEventPublisher).publish(eventCaptor.capture(), eq("outlookCalendarRequestEventSent"))

    val event = eventCaptor.firstValue
    assertNull(event.personReference)
    assertEquals(requestWithoutSms.supervisionAppointmentUrn, event.additionalInformation["supervisionAppointmentUrn"])
  }

  @Test
  fun `should propagate exception when domain event publication fails`() {
    val exception = RuntimeException("SNS publication failed")
    doThrow(exception).whenever(domainEventPublisher).publish(any(), eq("outlookCalendarRequestEventSent"))

    val thrown = assertThrows<RuntimeException> {
      service.buildAndPublishOutlookCalendarRequest(eventRequest)
    }

    assertEquals("SNS publication failed", thrown.message)
  }
}

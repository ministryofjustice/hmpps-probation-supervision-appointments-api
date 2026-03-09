package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message.DomainEventPublisher
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message.HmppsDomainEvent
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class DomainEventServiceTest {

  @Mock
  private lateinit var domainEventPublisher: DomainEventPublisher

  private lateinit var service: DomainEventService

  private val supervisionApiBaseUrl = "http://supervision-api"

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

    verify(domainEventPublisher).publish(eventCaptor.capture())

    val event = eventCaptor.firstValue

    assertEquals("probation-case.appointment.sms-sent", event.eventType)
    assertEquals("An sms has been sent, please create a contact", event.description)
    assertEquals(1, event.version)

    assertEquals(
      "$supervisionApiBaseUrl/sms/message?notificationId=$notificationId",
      event.detailUrl,
    )

    assertEquals("CRN", event.personReference?.identifiers?.first()?.type)
    assertEquals(crn, event.personReference?.identifiers?.first()?.value)

    assertTrue(event.additionalInformation!!.containsKey("applicationId"))

    assertNotNull(event.occurredAt)
  }
}

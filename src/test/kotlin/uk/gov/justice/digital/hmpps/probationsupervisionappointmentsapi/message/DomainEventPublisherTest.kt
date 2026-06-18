package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.TelemetryService
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsTopic
import uk.gov.justice.hmpps.sqs.publish
import kotlin.test.Test

@ExtendWith(MockitoExtension::class)
class DomainEventPublisherTest {
  @Mock
  lateinit var hmppsQueueService: HmppsQueueService

  @Mock
  lateinit var objectMapper: ObjectMapper

  @Mock
  lateinit var telemetryService: TelemetryService

  @Mock
  lateinit var topic: HmppsTopic

  @Mock
  lateinit var domainEvent: HmppsDomainEvent

  lateinit var domainEventPublisher: DomainEventPublisher

  @BeforeEach
  fun setUp() {
    whenever(hmppsQueueService.findByTopicId("hmppseventtopic"))
      .thenReturn(topic)

    domainEventPublisher = DomainEventPublisher(
      hmppsQueueService,
      objectMapper,
      telemetryService,
    )
  }

  @Test
  fun `should publish domain event and track success telemetry`() {
    doReturn("test.event").whenever(domainEvent).eventType
    doReturn(mapOf("CRN" to "X123456")).whenever(domainEvent).personReference

    whenever(objectMapper.writeValueAsString(domainEvent))
      .thenReturn("""{"eventType":"test.event"}""")

    domainEventPublisher.publish(domainEvent)

    verify(topic).publish(
      "test.event",
      """{"eventType":"test.event"}""",
    )

    verify(telemetryService).trackEvent(
      "PersonOnProbationSMSReminderSent",
      mapOf(
        "eventType" to "test.event",
        "crn" to "X123456",
      ),
    )

    verify(telemetryService, never()).trackException(any(), any())
  }

  @Test
  fun `should track failure telemetry and exception when publish throws exception`() {
    doReturn("test.event").whenever(domainEvent).eventType
    doReturn(mapOf("CRN" to "X123456")).whenever(domainEvent).personReference

    val exception = RuntimeException("Failed to publish")

    whenever(objectMapper.writeValueAsString(domainEvent))
      .thenReturn("""{"eventType":"test.event"}""")

    whenever(
      topic.publish(
        anyString(),
        anyString(),
      ),
    ).thenThrow(exception)

    domainEventPublisher.publish(domainEvent)

    verify(telemetryService).trackEvent(
      "PersonOnProbationSMSReminderSentFailed",
      mapOf(
        "eventType" to "test.event",
        "crn" to "X123456",
      ),
    )

    verify(telemetryService).trackException(
      exception,
      mapOf(
        "eventType" to "test.event",
        "crn" to "X123456",
      ),
    )
  }
}

package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.sentry.Sentry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.TelemetryService
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.ZonedDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class NotifyStatusCheckListenerTest {

  @Mock
  private lateinit var handler: NotifyStatusCheckHandler

  @Mock
  private lateinit var telemetryService: TelemetryService

  @Mock
  private lateinit var hmppsQueueService: HmppsQueueService

  private val objectMapper: ObjectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())

  private lateinit var listener: NotifyStatusCheckListener

  private val notificationId = UUID.randomUUID()

  @BeforeEach
  fun setUp() {
    listener = NotifyStatusCheckListener(handler, objectMapper, telemetryService, hmppsQueueService)
  }

  private fun queueWithMaxReceiveCount(maxReceiveCount: Int?): HmppsQueue {
    val queue = mock(HmppsQueue::class.java)
    whenever(queue.maxReceiveCount).thenReturn(maxReceiveCount)
    return queue
  }

  private fun snsEnvelope(notificationId: UUID = this.notificationId): String {
    val event = HmppsDomainEvent(
      eventType = "probation.appointment.sms-status-check-requested",
      version = 1,
      detailUrl = null,
      occurredAt = ZonedDateTime.now(),
      description = "Check SMS delivery status",
      additionalInformation = mapOf("notificationId" to notificationId.toString()),
      personReference = null,
    )
    val messageJson = objectMapper.writeValueAsString(event)
    val envelope = mapOf(
      "Type" to "Notification",
      "MessageId" to UUID.randomUUID().toString(),
      "TopicArn" to "arn:aws:sns:eu-west-2:000000000000:test-topic",
      "Message" to messageJson,
    )
    return objectMapper.writeValueAsString(envelope)
  }

  @Test
  fun `handler succeeds - tracks receipt, no exception telemetry or Sentry`() {
    mockStatic(Sentry::class.java).use { sentryMock ->
      listener.handleStatusCheck(snsEnvelope(), receiveCount = 1)

      verify(handler).handle(any())
      verifyMessageReceivedTracked()
      verifyNoExceptionTelemetryOrSentry(sentryMock)
    }
  }

  @Test
  fun `handler throws and this is not the final attempt - no exception telemetry or Sentry, still rethrows`() {
    mockStatic(Sentry::class.java).use { sentryMock ->
      whenever(hmppsQueueService.findByQueueId("notifystatuscheck")).thenReturn(queueWithMaxReceiveCount(3))
      val exception = RuntimeException("transient")
      doThrow(exception).whenever(handler).handle(any())

      assertThrows<RuntimeException> { listener.handleStatusCheck(snsEnvelope(), receiveCount = 1) }

      verifyMessageReceivedTracked()
      verifyNoExceptionTelemetryOrSentry(sentryMock)
    }
  }

  @Test
  fun `handler throws on the final attempt - tracks exception telemetry and Sentry, still rethrows`() {
    mockStatic(Sentry::class.java).use { sentryMock ->
      whenever(hmppsQueueService.findByQueueId("notifystatuscheck")).thenReturn(queueWithMaxReceiveCount(3))
      val exception = RuntimeException("final attempt")
      doThrow(exception).whenever(handler).handle(any())

      assertThrows<RuntimeException> { listener.handleStatusCheck(snsEnvelope(), receiveCount = 3) }

      verifyMessageReceivedTracked()
      verify(telemetryService).trackException(eq(exception), eq(emptyMap()), eq(emptyMap()))
      sentryMock.verify { Sentry.captureException(exception) }
    }
  }

  @Test
  fun `maxReceiveCount lookup returns null - falls back to a hardcoded 3 rather than always alerting`() {
    mockStatic(Sentry::class.java).use { sentryMock ->
      whenever(hmppsQueueService.findByQueueId("notifystatuscheck")).thenReturn(queueWithMaxReceiveCount(null))
      val exception = RuntimeException("not final attempt, by the hardcoded fallback")
      doThrow(exception).whenever(handler).handle(any())

      assertThrows<RuntimeException> { listener.handleStatusCheck(snsEnvelope(), receiveCount = 1) }

      verifyMessageReceivedTracked()
      verifyNoExceptionTelemetryOrSentry(sentryMock)
    }
  }

  @Test
  fun `queue not found - falls back to a hardcoded 3 rather than always alerting`() {
    mockStatic(Sentry::class.java).use { sentryMock ->
      whenever(hmppsQueueService.findByQueueId("notifystatuscheck")).thenReturn(null)
      val exception = RuntimeException("not final attempt, by the hardcoded fallback")
      doThrow(exception).whenever(handler).handle(any())

      assertThrows<RuntimeException> { listener.handleStatusCheck(snsEnvelope(), receiveCount = 3) }

      verifyMessageReceivedTracked()
      verify(telemetryService).trackException(eq(exception), eq(emptyMap()), eq(emptyMap()))
      sentryMock.verify { Sentry.captureException(exception) }
    }
  }

  private fun verifyMessageReceivedTracked() {
    verify(telemetryService).trackEvent(eq("NotifyStatusCheckMessageReceived"), any(), eq(emptyMap()))
  }

  private fun verifyNoExceptionTelemetryOrSentry(sentryMock: MockedStatic<Sentry>) {
    verify(telemetryService, never()).trackException(any(), any(), any())
    sentryMock.verifyNoInteractions()
  }
}

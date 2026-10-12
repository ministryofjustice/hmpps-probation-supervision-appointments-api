package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.events

import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.NotificationMapping
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message.DomainEventPublisher
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.TelemetryService
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import uk.gov.service.notify.Notification
import uk.gov.service.notify.NotificationClient
import uk.gov.service.notify.NotificationClientException
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.assertEquals

class NotifyStatusCheckListenerIntegrationTest : IntegrationTestBase() {

  @MockitoBean
  lateinit var notificationClient: NotificationClient

  @MockitoBean
  lateinit var telemetryService: TelemetryService

  @Autowired
  lateinit var domainEventPublisher: DomainEventPublisher

  @Autowired
  protected lateinit var hmppsQueueService: HmppsQueueService

  private val testQueue by lazy {
    hmppsQueueService.findByQueueId("notifystatuscheck")
      ?: throw RuntimeException("Queue with name notifystatuscheck doesn't exist")
  }
  private val testSqsClient by lazy { testQueue.sqsClient }
  private val testQueueUrl by lazy { testQueue.queueUrl }
  private val testDlqClient by lazy { testQueue.sqsDlqClient ?: throw RuntimeException("DLQ not configured for notifystatuscheck") }
  private val testDlqUrl by lazy { testQueue.dlqUrl ?: throw RuntimeException("DLQ not configured for notifystatuscheck") }

  private val deliusExternalReference = "delius-ref-1"

  @BeforeEach
  fun `clear queues`() {
    testSqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(testQueueUrl).build())
    testDlqClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(testDlqUrl).build())
  }

  private fun statusCheckEvent(notificationId: UUID) = HmppsDomainEvent(
    eventType = "probation.appointment.sms-status-check-requested",
    version = 1,
    detailUrl = null,
    occurredAt = ZonedDateTime.now(),
    description = "Check SMS delivery status",
    additionalInformation = mapOf("notificationId" to notificationId.toString()),
    personReference = null,
  )

  private fun saveMapping(notificationId: UUID) = notificationMappingRepository.save(
    NotificationMapping(
      deliusExternalReference = deliusExternalReference,
      notificationId = notificationId,
      templateId = UUID.randomUUID(),
      message = "body",
      status = null,
    ),
  )

  private fun notification(status: String): Notification {
    val notification = mock(Notification::class.java)
    whenever(notification.status).thenReturn(status)
    return notification
  }

  @DisplayName("Message processed successfully")
  @Nested
  inner class HappyPath {
    @Test
    fun `listener consumes message, calls Notify and persists the resulting status`() {
      val notificationId = UUID.randomUUID()
      saveMapping(notificationId)
      val delivered = notification("delivered")
      whenever(notificationClient.getNotificationById(notificationId.toString())).thenReturn(delivered)

      domainEventPublisher.publish(statusCheckEvent(notificationId))

      await untilCallTo {
        notificationMappingRepository.findByNotificationId(notificationId)?.status
      } matches { it == "delivered" }

      await untilCallTo {
        testSqsClient.countMessagesOnQueue(testQueueUrl).get()
      } matches { it == 0 }
      assertEquals(0, testDlqClient.countMessagesOnQueue(testDlqUrl).get())
    }
  }

  @DisplayName("Message fails on its final allowed attempt")
  @Nested
  inner class FinalAttemptFailure {
    @Test
    fun `listener tracks the exception and the message lands on the DLQ`() {
      val notificationId = UUID.randomUUID()
      saveMapping(notificationId)
      val serverError = mock(NotificationClientException::class.java)
      whenever(serverError.httpResult).thenReturn(500)
      whenever(notificationClient.getNotificationById(notificationId.toString())).thenThrow(serverError)

      domainEventPublisher.publish(statusCheckEvent(notificationId))

      // notifystatuscheck is configured in application-test.yml with dlqMaxReceiveCount: 1, so the
      // very first delivery is already the final attempt before the message moves to the DLQ.
      verify(telemetryService, timeout(10_000)).trackException(serverError)

      await untilCallTo {
        testDlqClient.countMessagesOnQueue(testDlqUrl).get()
      } matches { it == 1 }
    }
  }
}

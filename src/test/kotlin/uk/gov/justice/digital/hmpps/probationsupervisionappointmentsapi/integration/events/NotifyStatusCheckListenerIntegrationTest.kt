package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.events

import com.fasterxml.jackson.databind.ObjectMapper
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
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.NotificationMapping
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message.DomainEventPublisher
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.FeatureFlagsService
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

  @MockitoBean
  lateinit var featureFlagsService: FeatureFlagsService

  @Autowired
  lateinit var domainEventPublisher: DomainEventPublisher

  @Autowired
  lateinit var objectMapper: ObjectMapper

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

  private val domainEventsQueue by lazy {
    hmppsQueueService.findByQueueId("domaineventsqueue")
      ?: throw RuntimeException("Queue with name domaineventsqueue doesn't exist")
  }
  private val domainEventsSqsClient by lazy { domainEventsQueue.sqsClient }
  private val domainEventsQueueUrl by lazy { domainEventsQueue.queueUrl }

  private val deliusExternalReference = "delius-ref-1"

  @BeforeEach
  fun `clear queues`() {
    val purgeQueue = testSqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(testQueueUrl).build())
    val purgeDlq = testDlqClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(testDlqUrl).build())
    val purgeDomainEventsQueue = domainEventsSqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(domainEventsQueueUrl).build())
    purgeQueue.join()
    purgeDlq.join()
    purgeDomainEventsQueue.join()
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

  private fun saveMapping(notificationId: UUID, crn: String? = null) = notificationMappingRepository.save(
    NotificationMapping(
      deliusExternalReference = deliusExternalReference,
      notificationId = notificationId,
      templateId = UUID.randomUUID(),
      message = "body",
      status = null,
      crn = crn,
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

  @DisplayName("Confirmed delivery publishes contact event behind a feature flag")
  @Nested
  inner class ContactEventOnConfirmedDelivery {
    @Test
    fun `flag enabled and Notify confirms delivery - publishes contact event`() {
      whenever(featureFlagsService.isEnabled("contact-event-on-confirmed-delivery-toggle")).thenReturn(true)
      val notificationId = UUID.randomUUID()
      val crn = "X123456"
      saveMapping(notificationId, crn = crn)
      val delivered = notification("delivered")
      whenever(notificationClient.getNotificationById(notificationId.toString())).thenReturn(delivered)

      domainEventPublisher.publish(statusCheckEvent(notificationId))

      // domaineventsqueue has no eventType filter, so it also receives the sms-status-check-requested
      // event this test published directly above — wait for both, then fetch once and filter by type.
      await untilCallTo {
        domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get()
      } matches { it == 2 }

      val domainEvents = receiveDomainEvents(expectedCount = 2)
      val contactEvent = domainEvents.single { it.eventType == "probation.appointment.sms-sent-to-pop" }
      assertEquals(crn, contactEvent.personReference?.get("CRN"))
    }

    @Test
    fun `flag disabled and Notify confirms delivery - does not publish contact event`() {
      whenever(featureFlagsService.isEnabled("contact-event-on-confirmed-delivery-toggle")).thenReturn(false)
      val notificationId = UUID.randomUUID()
      saveMapping(notificationId, crn = "X123456")
      val delivered = notification("delivered")
      whenever(notificationClient.getNotificationById(notificationId.toString())).thenReturn(delivered)

      domainEventPublisher.publish(statusCheckEvent(notificationId))

      await untilCallTo {
        notificationMappingRepository.findByNotificationId(notificationId)?.status
      } matches { it == "delivered" }

      // Only the test's own sms-status-check-requested publish should be on the queue — no contact event.
      await untilCallTo {
        domainEventsSqsClient.countMessagesOnQueue(domainEventsQueueUrl).get()
      } matches { it == 1 }

      assertEquals(0, receiveDomainEvents(expectedCount = 1).count { it.eventType == "probation.appointment.sms-sent-to-pop" })
    }

    // SQS may return fewer messages than requested.
    // Keep polling until the expected number of messages has been received.
    private fun receiveDomainEvents(expectedCount: Int): List<HmppsDomainEvent> {
      val collected = mutableListOf<HmppsDomainEvent>()
      await untilCallTo {
        val messages = domainEventsSqsClient.receiveMessage(
          ReceiveMessageRequest.builder().queueUrl(domainEventsQueueUrl).maxNumberOfMessages(10).build(),
        ).join().messages()
        collected += messages.map {
          val snsEnvelope = objectMapper.readValue(it.body(), SnsNotification::class.java)
          objectMapper.readValue(snsEnvelope.Message, HmppsDomainEvent::class.java)
        }
        collected.size
      } matches { (it ?: 0) >= expectedCount }
      return collected
    }
  }
}

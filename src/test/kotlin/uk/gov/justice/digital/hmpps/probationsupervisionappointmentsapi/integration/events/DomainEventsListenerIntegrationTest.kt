package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.events

import com.fasterxml.jackson.databind.ObjectMapper
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.DomainEventService
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.util.UUID
import kotlin.test.assertEquals

class DomainEventsListenerIntegrationTest : IntegrationTestBase() {

  @Autowired
  protected lateinit var hmppsQueueService: HmppsQueueService

  @Autowired
  lateinit var domainEventService: DomainEventService

  @Autowired
  lateinit var objectMapper: ObjectMapper

  internal val testQueue by lazy {
    hmppsQueueService.findByQueueId("domaineventsqueue")
      ?: throw RuntimeException("Queue with name domaineventsqueue doesn't exist")
  }
  internal val testSqsClient by lazy { testQueue.sqsClient }
  internal val testQueueUrl by lazy { testQueue.queueUrl }

  @DisplayName("Publish Domain Event")
  @Nested
  inner class PublishDomainEvent {

    @BeforeEach
    fun `clear queues`() {
      testSqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(testQueueUrl).build())
    }

    @Test
    fun `should publish domain event`() {
      val crn = "X123456"
      val notificationId = UUID.randomUUID()
      await untilCallTo {
        testSqsClient.countMessagesOnQueue(testQueue.queueUrl).get()
      } matches { it == 0 }

      domainEventService.buildAndPublishContactEvent(crn, notificationId)

      await untilCallTo {
        testSqsClient.countMessagesOnQueue(testQueue.queueUrl).get()
      } matches { it == 1 }

      val sqsMessage = testSqsClient.receiveMessage(ReceiveMessageRequest.builder().queueUrl(testQueueUrl).build()).join().messages()[0].body()

      val snsEnvelope = objectMapper.readValue(
        sqsMessage,
        SnsNotification::class.java,
      )

      val domainEvent = objectMapper.readValue(
        snsEnvelope.Message,
        HmppsDomainEvent::class.java,
      )

      assertEquals("probation.appointment.sms-sent-to-pop", domainEvent.eventType)
      assertEquals("http://localhost:9104/sms/message?notificationId=$notificationId", domainEvent.detailUrl)
      assertEquals(crn, domainEvent.personReference?.get("CRN"))
    }
  }
}
data class SnsNotification(
  val Type: String,
  val MessageId: String,
  val TopicArn: String,
  val Message: String,
)

package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.events

import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.DomainEventService
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.util.UUID

class DomainEventsListenerIntegrationTest : IntegrationTestBase() {

  @Autowired
  protected lateinit var hmppsQueueService: HmppsQueueService

  @Autowired
  lateinit var domainEventService: DomainEventService

  internal val testQueue by lazy {
    hmppsQueueService.findByQueueId("domaineventsqueue")
      ?: throw RuntimeException("Queue with name domaineventstestqueue doesn't exist")
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

      val buildAndPublishContactEvent = domainEventService.buildAndPublishContactEvent(crn, notificationId)

      await untilCallTo {
        testSqsClient.countMessagesOnQueue(testQueue.queueUrl).get()
      } matches { it == 1 }

      val returnResult = buildAndPublishContactEvent?.messageId()
      println(returnResult)
    }
  }
}

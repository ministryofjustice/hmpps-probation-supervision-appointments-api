package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.event

import com.fasterxml.jackson.annotation.JsonProperty
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.test.context.ActiveProfiles
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.DomainEventService
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.util.UUID

@SpringBootTest(webEnvironment = RANDOM_PORT, properties = ["spring.autoconfigure.exclude=uk.gov.justice.hmpps.sqs.HmppsSqsConfiguration"])
@ActiveProfiles("test")
@Order(1)
class DomainEventsListenerIntegrationTest : IntegrationTestBase() {

  @Autowired
  lateinit var domainEventService: DomainEventService

  @Test
  @Disabled("yet to fixed - wip")
  fun `should publish domain event`() {
    val crn = "X123456"
    val notificationId = UUID.randomUUID()
    await untilCallTo {
      domainEventQueueClient.countMessagesOnQueue(domainEventQueue.queueUrl).get()
    } matches { it == 0 }

    domainEventService.buildAndPublishContactEvent(crn, notificationId)

    await untilCallTo {
      domainEventQueueClient.countMessagesOnQueue(domainEventQueue.queueUrl).get()
    } matches { it == 1 }
  }

  fun sendDomainEvent(
    message: HmppsDomainEvent,
    queueUrl: String = domainEventQueue.queueUrl,
  ): SendMessageResponse = domainEventQueueClient.sendMessage(
    SendMessageRequest.builder()
      .queueUrl(queueUrl)
      .messageBody(
        objectMapper.writeValueAsString(SQSMessage(objectMapper.writeValueAsString(message))),
      ).build(),
  ).get()
}

data class SQSMessage(
  @JsonProperty("Message") val message: String,
)

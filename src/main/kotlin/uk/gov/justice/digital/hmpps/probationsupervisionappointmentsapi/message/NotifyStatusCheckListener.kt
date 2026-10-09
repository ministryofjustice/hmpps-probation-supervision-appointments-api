package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message

import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import io.sentry.Sentry
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.Header
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.TelemetryService
import uk.gov.justice.hmpps.sqs.HmppsQueueService

private const val QUEUE_ID = "notifystatuscheck"

// Mirrors this queue's actual maxReceiveCount, as configured in the cloud-platform-environments
// terraform (supervision-appointments-sms-status-check-queue.tf). The AWS lookup below normally
// fetches this same value live; this is only used if that one-off lookup is ever unavailable, so
// we fail open rather than alerting on every retry instead of just the final one.
private const val FALLBACK_MAX_RECEIVE_COUNT = 3

@Service
class NotifyStatusCheckListener(
  private val handler: NotifyStatusCheckHandler,
  private val objectMapper: ObjectMapper,
  private val telemetryService: TelemetryService,
  private val hmppsQueueService: HmppsQueueService,
) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  fun handleStatusCheck(
    rawSnsEnvelope: String,
    @Header("Sqs_Msa_ApproximateReceiveCount") receiveCount: Int,
  ) {
    val messageJson = objectMapper.readTree(rawSnsEnvelope).get("Message").asText()
    val event = objectMapper.readValue(messageJson, HmppsDomainEvent::class.java)
    telemetryService.trackEvent(
      "NotifyStatusCheckMessageReceived",
      mapOf("eventType" to event.eventType),
    )

    try {
      handler.handle(event)
    } catch (e: Exception) {
      val maxReceiveCount = hmppsQueueService.findByQueueId(QUEUE_ID)?.maxReceiveCount ?: FALLBACK_MAX_RECEIVE_COUNT
      if (receiveCount >= maxReceiveCount) {
        telemetryService.trackException(e)
        Sentry.captureException(e)
      } else {
        log.warn("Error handling notify status check message, attempt {}/{}", receiveCount, maxReceiveCount, e)
      }

      throw e // rethrow: SQS redelivers per the queue's redrive policy, or DLQs on the final attempt
    }
  }
}

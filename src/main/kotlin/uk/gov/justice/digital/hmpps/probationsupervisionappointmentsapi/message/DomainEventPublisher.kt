package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.TelemetryService
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.publish

@Service
class DomainEventPublisher(
  private val hmppsQueueService: HmppsQueueService,
  private val objectMapper: ObjectMapper,
  private val telemetryService: TelemetryService,
) {
  private val domainEventsTopic by lazy {
    hmppsQueueService.findByTopicId("hmppseventtopic") ?: throw IllegalStateException("hmppseventtopic not found")
  }
  fun publish(domainEvent: HmppsDomainEvent) {
    val telemetryProperties = mapOf(
      "eventType" to domainEvent.eventType,
      "crn" to domainEvent.personReference?.get("CRN"),
    )

    try {
      val response = domainEventsTopic.publish(
        domainEvent.eventType,
        objectMapper.writeValueAsString(domainEvent),
      )
      telemetryService.trackEvent("smsContactEventSent", telemetryProperties)
      LOG.info("Published event to outbound topic, eventType={}, messageId={}", domainEvent.eventType, response.messageId())
      LOG.debug("Event contains person reference, messageId={}, identifiers={}", response.messageId(), domainEvent.personReference?.identifiers)
    } catch (ex: Exception) {
      LOG.warn("Failed to publish event to outbound topic, eventType={}, message: {}", domainEvent.eventType, ex.message)
      telemetryService.trackEvent("smsContactEventFailed", telemetryProperties)
    }
  }

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(DomainEventPublisher::class.java)
  }
}

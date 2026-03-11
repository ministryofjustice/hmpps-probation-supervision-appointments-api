package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sns.model.PublishResponse
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
  fun publish(domainEvent: HmppsDomainEvent): PublishResponse? {
    val telemetryProperties = mapOf(
      "eventType" to domainEvent.eventType,
      "crn" to domainEvent.personReference?.get("CRN"),
    )

    try {
      return domainEventsTopic.publish(
        domainEvent.eventType,
        objectMapper.writeValueAsString(domainEvent),
      )
      telemetryService.trackEvent("smsContactEventSent", telemetryProperties)
    } catch (ex: Exception) {
      telemetryService.trackEvent("smsContactEventFailed", telemetryProperties)
      telemetryService.trackException(ex, telemetryProperties)
    }
    return null
  }

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(DomainEventPublisher::class.java)
  }
}

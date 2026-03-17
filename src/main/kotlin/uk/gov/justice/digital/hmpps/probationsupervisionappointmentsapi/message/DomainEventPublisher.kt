package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message

import com.fasterxml.jackson.databind.ObjectMapper
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
      domainEventsTopic.publish(
        domainEvent.eventType,
        objectMapper.writeValueAsString(domainEvent),
      )
      telemetryService.trackEvent("smsContactEventSent", telemetryProperties)
    } catch (ex: Exception) {
      telemetryService.trackEvent("smsContactEventFailed", telemetryProperties)
      telemetryService.trackException(ex, telemetryProperties)
    }
  }
}

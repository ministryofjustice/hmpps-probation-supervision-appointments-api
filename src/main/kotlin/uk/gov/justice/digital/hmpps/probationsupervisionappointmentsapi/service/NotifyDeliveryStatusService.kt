package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import io.sentry.Sentry
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.NotificationMappingRepository
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message.DELIVERED_STATUS
import java.time.Instant
import java.util.UUID

@Service
class NotifyDeliveryStatusService(
  private val notificationMappingRepository: NotificationMappingRepository,
  private val domainEventService: DomainEventService,
  private val featureFlagsService: FeatureFlagsService,
  private val telemetryService: TelemetryService,
) {
  // Roll back the status update if publishing the contact event fails,
  // allowing the notification to be processed again.
  @Transactional
  fun recordStatus(notificationId: UUID, crn: String?, status: String): Int {
    val updated = notificationMappingRepository.updateStatus(
      notificationId = notificationId,
      status = status,
      updatedAt = Instant.now(),
    )

    // Prevents duplicate contact events if the feature flag changes
    // between sending the SMS and confirming delivery.
    if (updated > 0 &&
      status == DELIVERED_STATUS &&
      crn != null &&
      featureFlagsService.isEnabled(CONTACT_EVENT_ON_CONFIRMED_DELIVERY_FLAG) &&
      notificationMappingRepository.markContactEventPublished(notificationId, Instant.now()) > 0
    ) {
      try {
        domainEventService.buildAndPublishContactEvent(crn = crn, notificationId = notificationId)
      } catch (ex: Exception) {
        // Roll back the database changes so the notification can be retried.
        // Record the failure, as the event may already have been published.
        telemetryService.trackEvent(
          "ContactEventPublishRolledBack",
          mapOf("notificationId" to notificationId.toString(), "crn" to crn),
        )
        telemetryService.trackException(ex, mapOf("notificationId" to notificationId.toString()))
        Sentry.captureException(ex)
        throw ex
      }
    }

    return updated
  }
}

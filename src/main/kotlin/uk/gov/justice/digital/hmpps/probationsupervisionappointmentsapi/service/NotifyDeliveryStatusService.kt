package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import io.sentry.Sentry
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.NotificationMappingRepository
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message.FINAL_NOTIFY_STATUSES
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
  // allowing the notification to be processed again. rollbackFor is required because Spring's
  // default rollback rules only cover unchecked exceptions, not checked ones (e.g. Jackson's
  // JsonProcessingException from DomainEventPublisher's serialization).
  // alreadyPublishedAt is set when the crn was recovered from Notify's reference for a
  // historical row (mapping.crn was null) - its contact event was already sent via the old
  // send-time code path before the crn column existed, regardless of the eventual delivery
  // outcome, so this backfill runs for any final status and does not depend on the feature flag.
  @Transactional(rollbackFor = [Exception::class])
  fun recordStatus(notificationId: UUID, crn: String?, status: String, alreadyPublishedAt: Instant? = null, createdAt: Instant): Int {
    val updated = notificationMappingRepository.updateStatus(
      notificationId = notificationId,
      status = status,
      updatedAt = Instant.now(),
    )

    if (updated > 0 && crn != null) {
      if (alreadyPublishedAt != null) {
        notificationMappingRepository.markContactEventPublished(notificationId, createdAt)
      } else if (
        status in FINAL_NOTIFY_STATUSES &&
        // Prevents duplicate contact events if the feature flag changes
        // between sending the SMS and confirming delivery.
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
    }

    return updated
  }
}

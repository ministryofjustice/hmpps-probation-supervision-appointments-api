package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

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
      featureFlagsService.isEnabled(CONTACT_EVENT_ON_CONFIRMED_DELIVERY_FLAG) &&
      notificationMappingRepository.markContactEventPublished(notificationId, Instant.now()) > 0
    ) {
      domainEventService.buildAndPublishContactEvent(crn = crn!!, notificationId = notificationId)
    }

    return updated
  }
}

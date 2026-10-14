package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.NotificationMappingRepository
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.PENDING_NOTIFY_STATUSES
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.CONTACT_EVENT_ON_CONFIRMED_DELIVERY_FLAG
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.DomainEventService
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.FeatureFlagsService
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.TelemetryService
import uk.gov.service.notify.NotificationClient
import uk.gov.service.notify.NotificationClientException
import java.time.Instant
import java.util.UUID

val FAILURE_STATUSES = setOf("permanent-failure", "temporary-failure", "technical-failure")
const val NOT_FOUND_STATUS = "not-found"
const val DELIVERED_STATUS = "delivered"

@Component
class NotifyStatusCheckHandler(
  private val notificationMappingRepository: NotificationMappingRepository,
  private val notificationClient: NotificationClient,
  private val telemetryService: TelemetryService,
  private val domainEventService: DomainEventService,
  private val featureFlagsService: FeatureFlagsService,
) {
  // Not @Transactional: the Notify API call below is a slow, synchronous network request, and
  // holding a DB connection open for its duration under concurrent SQS deliveries could exhaust
  // the connection pool. Each DB write (updateStatus) carries its own short transaction instead.
  fun handle(event: HmppsDomainEvent) {
    val notificationId = UUID.fromString(event.additionalInformation["notificationId"] as String)

    val mapping = notificationMappingRepository.findByNotificationId(notificationId)
    if (mapping == null) {
      // Do not treat as a failure requiring redelivery.
      // Incorrect message in queue
      telemetryService.trackEvent(
        "NotifyStatusCheckOrphanMessage",
        mapOf("notificationId" to notificationId.toString()),
      )
      return
    }

    if (mapping.status != null && mapping.status !in PENDING_NOTIFY_STATUSES) {
      // Message has already been processed
      return
    }

    val notification = try {
      notificationClient.getNotificationById(notificationId.toString())
    } catch (e: NotificationClientException) {
      if (e.httpResult == 404) {
        // Notify retains notifications for max 90 days — Notify will return a 404, record not found.
        val updated = notificationMappingRepository.updateStatus(
          notificationId = notificationId,
          status = NOT_FOUND_STATUS,
          updatedAt = Instant.now(),
        )
        if (updated > 0) {
          telemetryService.trackEvent(
            "NotifyStatusCheckNotFound",
            mapOf("notificationId" to notificationId.toString(), "deliusExternalReference" to mapping.deliusExternalReference),
          )
        }
        return
      }

      throw e
    }

    // Record the current status regardless of whether it's still in-flight or terminal.
    // If another concurrent delivery already recorded a terminal status, this update is a no-op
    // (see the conditional WHERE clause on updateStatus), and `updated` will be 0.
    val updated = notificationMappingRepository.updateStatus(
      notificationId = notificationId,
      status = notification.status,
      updatedAt = Instant.now(),
    )

    if (notification.status in PENDING_NOTIFY_STATUSES) {
      // Still in-flight at Notify's end — the row stays "unresolved" and is naturally
      // re-picked-up by the next status-check sweep.
      return
    }

    if (updated > 0 && notification.status in FAILURE_STATUSES) {
      telemetryService.trackEvent(
        "SmsDeliveryFailed",
        mapOf(
          "notificationId" to notificationId.toString(),
          "deliusExternalReference" to mapping.deliusExternalReference,
          "status" to notification.status,
        ),
      )
    }

    if (updated > 0 &&
      notification.status == DELIVERED_STATUS &&
      featureFlagsService.isEnabled(CONTACT_EVENT_ON_CONFIRMED_DELIVERY_FLAG)
    ) {
      domainEventService.buildAndPublishContactEvent(
        crn = mapping.crn!!,
        notificationId = notificationId,
      )
    }
  }
}

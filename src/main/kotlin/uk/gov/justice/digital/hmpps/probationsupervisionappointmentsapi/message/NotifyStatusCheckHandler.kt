package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.NotificationMappingRepository
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.PENDING_NOTIFY_STATUSES
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.TelemetryService
import uk.gov.service.notify.NotificationClient
import uk.gov.service.notify.NotificationClientException
import java.time.Instant
import java.util.UUID

val FAILURE_STATUSES = setOf("permanent-failure", "temporary-failure", "technical-failure")
const val NOT_FOUND_STATUS = "not-found"

@Component
class NotifyStatusCheckHandler(
  private val notificationMappingRepository: NotificationMappingRepository,
  private val notificationClient: NotificationClient,
  private val telemetryService: TelemetryService,
) {
  @Transactional
  fun handle(event: HmppsDomainEvent) {
    val notificationId = UUID.fromString(event.additionalInformation["notificationId"] as String)

    val mapping = notificationMappingRepository.findByNotificationId(notificationId)
    if (mapping == null) {
      // do not treat as a failure requiring redelivery.
      telemetryService.trackEvent(
        "NotifyStatusCheckOrphanMessage",
        mapOf("notificationId" to notificationId.toString()),
      )
      return
    }

    if (mapping.status != null && mapping.status !in PENDING_NOTIFY_STATUSES) {
      // Already resolved to a terminal status by an earlier (possibly redelivered/duplicate)
      // message — no-op.
      return
    }

    val notification = try {
      notificationClient.getNotificationById(notificationId.toString())
    } catch (e: NotificationClientException) {
      if (e.httpResult == 404) {
        // Notify retains notifications for max 90 days — Notify will 404, record as not found in DB.
        notificationMappingRepository.updateStatus(
          notificationId = notificationId,
          status = NOT_FOUND_STATUS,
          updatedAt = Instant.now(),
        )
        telemetryService.trackEvent(
          "NotifyStatusCheckNotFound",
          mapOf("notificationId" to notificationId.toString(), "deliusExternalReference" to mapping.deliusExternalReference),
        )
        return
      }

      throw e
    }

    // Record the current status regardless of whether it's still in-flight or terminal.
    notificationMappingRepository.updateStatus(
      notificationId = notificationId,
      status = notification.status,
      updatedAt = Instant.now(),
    )

    if (notification.status in PENDING_NOTIFY_STATUSES) {
      // Still in-flight at Notify's end — the row stays "unresolved" and is naturally
      // re-picked-up by the next status-check sweep.
      return
    }

    if (notification.status in FAILURE_STATUSES) {
      telemetryService.trackEvent(
        "SmsDeliveryFailed",
        mapOf(
          "notificationId" to notificationId.toString(),
          "deliusExternalReference" to mapping.deliusExternalReference,
          "status" to notification.status,
        ),
      )
    }
  }
}

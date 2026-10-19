package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.NotificationMappingRepository
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.PENDING_NOTIFY_STATUSES
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.NotifyDeliveryStatusService
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.TelemetryService
import uk.gov.service.notify.NotificationClient
import uk.gov.service.notify.NotificationClientException
import java.time.Instant
import java.util.UUID

val FAILURE_STATUSES = setOf("permanent-failure", "temporary-failure", "technical-failure")
const val NOT_FOUND_STATUS = "not-found"
const val DELIVERED_STATUS = "delivered"
val DELIVERED_STATUSES = setOf(DELIVERED_STATUS) + FAILURE_STATUSES

@Component
class NotifyStatusCheckHandler(
  private val notificationMappingRepository: NotificationMappingRepository,
  private val notificationClient: NotificationClient,
  private val telemetryService: TelemetryService,
  private val notifyDeliveryStatusService: NotifyDeliveryStatusService,
) {
  // Keep the Notify API call outside a database transaction to avoid holding
  // database connections open during network requests.
  // NotifyDeliveryStatusService handles the status update and contact event publishing.
  fun handle(event: HmppsDomainEvent) {
    val notificationId = UUID.fromString(event.additionalInformation["notificationId"] as String)

    val mapping = notificationMappingRepository.findByNotificationId(notificationId)
    if (mapping == null) {
      // No matching notification found. Record the event without triggering a retry.
      telemetryService.trackEvent(
        "NotifyStatusCheckOrphanMessage",
        mapOf("notificationId" to notificationId.toString()),
      )
      return
    }

    if (mapping.status != null && mapping.status !in PENDING_NOTIFY_STATUSES) {
      // The notification already has a final status, so no further processing is needed.
      return
    }

    val notification = try {
      notificationClient.getNotificationById(notificationId.toString())
    } catch (e: NotificationClientException) {
      if (e.httpResult == 404) {
        // Notify could not find the notification. Record its status as not-found.
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

    // Update the notification status and publish a contact event if delivered.
    // The database check prevents an existing final status from being overwritten.
    // Historical records without a CRN have it recovered from Notify's reference.
    // As their contact event was already published when the SMS was sent,
    // use the mapping row's createdAt to mark it as published and prevent duplicates.
    val updated = notifyDeliveryStatusService.recordStatus(
      notificationId = notificationId,
      crn = mapping.crn ?: notification.reference.orElse(null),
      status = notification.status,
      alreadyPublishedAt = if (mapping.crn == null) mapping.createdAt else null,
      createdAt = mapping.createdAt,
    )

    if (notification.status in PENDING_NOTIFY_STATUSES) {
      // The notification is still pending and will be checked again on the next scheduled run.
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
  }
}

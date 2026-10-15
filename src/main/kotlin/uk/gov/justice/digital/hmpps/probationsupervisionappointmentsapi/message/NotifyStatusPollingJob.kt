package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message

import io.sentry.Sentry
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.NotificationMappingRepository
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.FeatureFlagsService
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.TelemetryService
import java.time.ZonedDateTime

private const val NOTIFY_STATUS_SWEEP_FLAG = "notify-status-sweep-toggle"

// Periodically checks for unresolved SMS notifications and publishes a status-check event for each.
// This provides a fallback if the original event was missed or the notification is still pending.
// ShedLock ensures only one pod runs the job at a time.
@Component
class NotifyStatusPollingJob(
  private val notificationMappingRepository: NotificationMappingRepository,
  private val domainEventPublisher: DomainEventPublisher,
  private val telemetryService: TelemetryService,
  private val featureFlagsService: FeatureFlagsService,
  @Value("\${notify.status-check.batch-size:100}") private val batchSize: Int,
) {
  @Scheduled(cron = "\${notify.status-check-sweep.cron:0 */30 * * * *}")
  @SchedulerLock(
    name = "notifyStatusPolling",
    lockAtMostFor = "\${notify.status-check-sweep.lock-at-most-for:10m}",
    lockAtLeastFor = "\${notify.status-check-sweep.lock-at-least-for:1m}",
  )
  fun poll() {
    if (!featureFlagsService.isEnabled(NOTIFY_STATUS_SWEEP_FLAG)) return

    val unresolved = notificationMappingRepository.findUnresolved(limit = batchSize)
    telemetryService.trackEvent("NotifyStatusPollingStarted", mapOf("unresolvedCount" to unresolved.size.toString()))

    unresolved.forEach { mapping ->
      try {
        domainEventPublisher.publish(
          HmppsDomainEvent(
            eventType = "probation.appointment.sms-status-check-requested",
            version = 1,
            detailUrl = null,
            occurredAt = ZonedDateTime.now(),
            description = "Check SMS delivery status",
            additionalInformation = mapOf("notificationId" to mapping.notificationId.toString()),
            personReference = null,
          ),
        )
      } catch (e: Exception) {
        // One row's publish failing should not prevent the rest of the sweep from running —
        // the row stays unresolved and will naturally be retried on the next run.
        telemetryService.trackException(e, mapOf("notificationId" to mapping.notificationId.toString()))
        Sentry.captureException(e)
      }
    }
  }
}

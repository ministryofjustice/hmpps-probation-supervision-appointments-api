package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration

import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.NotificationMapping
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Exercises NotificationMappingRepository.updateStatus directly against real Postgres, proving the
 * conditional WHERE clause stops a delayed/duplicate SQS delivery from overwriting an already-terminal
 * status (see NotifyStatusCheckHandler, which relies on the affected row count to decide whether it
 * "won" the race and should emit telemetry).
 */
class NotificationMappingRepositoryIntegrationTest : IntegrationTestBase() {

  private fun saveMapping(notificationId: UUID) = notificationMappingRepository.save(
    NotificationMapping(
      deliusExternalReference = "delius-ref-1",
      notificationId = notificationId,
      templateId = UUID.randomUUID(),
      message = "body",
      status = null,
    ),
  )

  @Test
  fun `updateStatus transitions an unresolved row and reports one row affected`() {
    val notificationId = UUID.randomUUID()
    saveMapping(notificationId)

    val rowsUpdated = notificationMappingRepository.updateStatus(
      notificationId = notificationId,
      status = "delivered",
      updatedAt = Instant.now(),
    )

    assertEquals(1, rowsUpdated)
    assertEquals("delivered", notificationMappingRepository.findByNotificationId(notificationId)?.status)
  }

  // Proves: updateStatus does not overwrite an existing terminal status with a stale non-terminal update.
  @Test
  fun `a late sending update after permanent-failure is recorded leaves the status unchanged and reports zero rows affected`() {
    val notificationId = UUID.randomUUID()
    saveMapping(notificationId)

    // The "winning" handler records the terminal status first.
    val winner = notificationMappingRepository.updateStatus(
      notificationId = notificationId,
      status = "permanent-failure",
      updatedAt = Instant.now(),
    )
    assertEquals(1, winner)

    // A delayed, duplicate SQS delivery for the same notification then tries to record a stale,
    // non-terminal status — this must not overwrite the terminal result already persisted.
    val loser = notificationMappingRepository.updateStatus(
      notificationId = notificationId,
      status = "sending",
      updatedAt = Instant.now(),
    )

    assertEquals(0, loser)
    assertEquals("permanent-failure", notificationMappingRepository.findByNotificationId(notificationId)?.status)
  }

  // Proves: markContactEventPublished is an atomic "claim" - only the first caller for a given
  // notification can successfully flip the column from NULL, guarding against the send-time and
  // delivery-time paths both publishing a contact event when the feature flag flips in between.
  @Test
  fun `markContactEventPublished only lets the first caller claim the row`() {
    val notificationId = UUID.randomUUID()
    saveMapping(notificationId)

    val first = notificationMappingRepository.markContactEventPublished(notificationId, Instant.now())
    val second = notificationMappingRepository.markContactEventPublished(notificationId, Instant.now())

    assertEquals(1, first)
    assertEquals(0, second)
    assertEquals(
      true,
      notificationMappingRepository.findByNotificationId(notificationId)?.contactEventPublishedAt != null,
    )
  }
}

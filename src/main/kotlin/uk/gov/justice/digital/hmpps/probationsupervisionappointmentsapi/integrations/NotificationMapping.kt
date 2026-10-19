package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.exception.NotFoundException
import java.time.Instant
import java.util.UUID

// GOV.UK Notify statuses that indicate an SMS is still being processed.
val PENDING_NOTIFY_STATUSES = setOf("created", "sending", "pending")

@Entity
@Table(name = "notification_mappings")
@EntityListeners(AuditingEntityListener::class)
class NotificationMapping(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0,

  @Column(name = "delius_external_reference", nullable = false)
  val deliusExternalReference: String,

  @Column(name = "notificationid", nullable = false)
  val notificationId: UUID,

  @Column(name = "templateid", nullable = false)
  val templateId: UUID,

  @Column(nullable = false)
  val message: String,

  @Column(name = "crn")
  val crn: String? = null,

  @Column(name = "status")
  val status: String? = null,

  @Column(name = "status_updated_at")
  val statusUpdatedAt: Instant? = null,

  @Column(name = "contact_event_published_at")
  val contactEventPublishedAt: Instant? = null,
) {
  @CreatedDate
  @Column(name = "created_at", nullable = false)
  var createdAt: Instant = Instant.now()
}

interface NotificationMappingRepository : JpaRepository<NotificationMapping, Long> {
  fun findByDeliusExternalReference(deliusExternalReference: String): List<NotificationMapping>

  fun findByNotificationId(notificationId: UUID): NotificationMapping?

  // Finds notifications that are still pending or have no recorded status.
  // These remain eligible for status checks until a final status is recorded.
  @Query(
    "SELECT n FROM NotificationMapping n WHERE n.status IS NULL OR n.status IN :pendingStatuses ORDER BY n.createdAt ASC LIMIT :limit",
  )
  fun findUnresolved(
    @Param("pendingStatuses") pendingStatuses: Collection<String> = PENDING_NOTIFY_STATUSES,
    @Param("limit") limit: Int,
  ): List<NotificationMapping>

  // Updates the status only if the notification is still unresolved.
  // Prevents duplicate or delayed messages from overwriting a final status.
  // Returns the number of rows updated so the caller can identify whether the update succeeded.
  // crn is only backfilled when the row doesn't already have one (COALESCE), so historical
  // rows self-heal with the crn recovered from Notify's reference without clobbering real data.
  @Modifying
  @Transactional
  @Query(
    """
    UPDATE NotificationMapping n SET n.status = :status, n.statusUpdatedAt = :updatedAt, n.crn = COALESCE(n.crn, :crn)
    WHERE n.notificationId = :notificationId AND (n.status IS NULL OR n.status IN :pendingStatuses)
    """,
  )
  fun updateStatus(
    @Param("notificationId") notificationId: UUID,
    @Param("status") status: String,
    @Param("updatedAt") updatedAt: Instant,
    @Param("crn") crn: String?,
    @Param("pendingStatuses") pendingStatuses: Collection<String> = PENDING_NOTIFY_STATUSES,
  ): Int

  // Prevents the same contact event from being published twice.
  // Both  the send and delivery paths may attempt to publish if the feature flag changes.
  // Only the first update succeeds; subsequent attempts return 0 and skip publishing.
  @Modifying
  @Transactional
  @Query(
    "UPDATE NotificationMapping n SET n.contactEventPublishedAt = :publishedAt " +
      "WHERE n.notificationId = :notificationId AND n.contactEventPublishedAt IS NULL",
  )
  fun markContactEventPublished(
    @Param("notificationId") notificationId: UUID,
    @Param("publishedAt") publishedAt: Instant,
  ): Int
}

fun NotificationMappingRepository.getNotificationMappingByNotificationId(notificationId: UUID) = findByNotificationId(notificationId)
  ?: throw NotFoundException("NotificationMapping", "notificationId", notificationId)

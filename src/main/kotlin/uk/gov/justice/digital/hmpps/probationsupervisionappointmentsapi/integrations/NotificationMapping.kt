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
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.exception.NotFoundException
import java.time.Instant
import java.util.UUID

// GOV.UK Notify statuses that aren't terminal yet — the message is still in flight.
val PENDING_NOTIFY_STATUSES = setOf("created", "sending")

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
) {
  @CreatedDate
  @Column(name = "created_at", nullable = false)
  var createdAt: Instant = Instant.now()
}

interface NotificationMappingRepository : JpaRepository<NotificationMapping, Long> {
  fun findByDeliusExternalReference(deliusExternalReference: String): List<NotificationMapping>

  fun findByNotificationId(notificationId: UUID): NotificationMapping?

  @Query(
    "SELECT n FROM NotificationMapping n WHERE (n.status IS NULL OR n.status IN :pendingStatuses) AND n.createdAt > :createdAfter",
  )
  fun findUnresolvedCreatedAfter(
    @Param("createdAfter") createdAfter: Instant,
    @Param("pendingStatuses") pendingStatuses: Collection<String> = PENDING_NOTIFY_STATUSES,
  ): List<NotificationMapping>

  @Modifying
  @Query(
    "UPDATE NotificationMapping n SET n.status = :status, n.statusUpdatedAt = :updatedAt WHERE n.notificationId = :notificationId",
  )
  fun updateStatus(
    @Param("notificationId") notificationId: UUID,
    @Param("status") status: String,
    @Param("updatedAt") updatedAt: Instant,
  )
}

fun NotificationMappingRepository.getNotificationMappingByNotificationId(notificationId: UUID) = findByNotificationId(notificationId)
  ?: throw NotFoundException("NotificationMapping", "notificationId", notificationId)

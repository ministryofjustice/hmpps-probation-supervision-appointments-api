package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.exception.NotFoundException
import java.time.Instant
import java.util.UUID

@Immutable
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
) {
  @CreatedDate
  @Column(name = "created_at", nullable = false)
  var createdAt: Instant = Instant.now()
}

interface NotificationMappingRepository : JpaRepository<NotificationMapping, Long> {
  fun findByDeliusExternalReference(deliusExternalReference: String): List<NotificationMapping>

  fun findByNotificationId(notificationId: UUID): NotificationMapping?
}

fun NotificationMappingRepository.getNotificationMappingByNotificationId(notificationId: UUID) = findByNotificationId(notificationId)
  ?: throw NotFoundException("NotificationMapping", "notificationId", notificationId)

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
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

@Immutable
@Entity
@Table(name = "delius_outlook_mappings")
@EntityListeners(AuditingEntityListener::class)
class DeliusOutlookMapping(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0,

  @Column(nullable = false)
  val supervisionAppointmentUrn: String,

  @Column(nullable = false)
  val outlookId: String,

) {
  @CreatedDate
  @Column(nullable = false)
  var createdAt: Instant = Instant.now()

  @LastModifiedDate
  @Column(nullable = false)
  var updatedAt: Instant = Instant.now()
}

interface DeliusOutlookMappingRepository : JpaRepository<DeliusOutlookMapping, Long> {
  fun findBySupervisionAppointmentUrn(supervisionAppointmentUrn: String): DeliusOutlookMapping?
}

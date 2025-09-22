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
import java.time.Instant

@Immutable
@Entity
@Table(name = "delius_outlook_mappings")
@EntityListeners(AuditingEntityListener::class)
class DeliusOutlookMapping(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long,

  @Column(nullable = false)
  val deliusExternalReference: String,

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

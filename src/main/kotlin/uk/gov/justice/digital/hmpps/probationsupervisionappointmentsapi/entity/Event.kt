package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Column

@Entity
@Table(name = "events")
data class Event(
  @Id
  val id: String,

  @Column(name = "external_ref")
  val externalRef: String? = null,

  @Column(name = "outlook_id")
  val outlookId: String? = null,

  @Column(name = "subject")
  val subject: String,

  @Column(name = "start_time")
  val startTime: String,

  @Column(name = "end_time")
  val endTime: String,

  @Column(name = "attendees")
  val attendees: String // comma-separated emails
)

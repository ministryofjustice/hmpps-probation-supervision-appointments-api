package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response

import java.time.OffsetDateTime

data class SegmentResponse(
  val id: String,
  val key: String,
  val name: String,
  val description: String?,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
  val constraints: List<Constraint>,
  val matchType: String,
)

data class Constraint(
  val id: String,
  val property: String,
  val type: String,
  val operator: String,
  val value: String,
  val description: String?,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)

package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message

import java.time.ZonedDateTime

data class HmppsDomainEvent(
  val eventType: String,
  val version: Int,
  val detailUrl: String?,
  val occurredAt: ZonedDateTime,
  val description: String,
  val additionalInformation: Map<String, Any>? = mapOf(),
  val personReference: PersonReference?,
)

data class PersonReference(val identifiers: List<PersonIdentifier>) {
  data class PersonIdentifier(val type: String, val value: String)
  operator fun get(key: String) = identifiers.find { it.type == key }?.value
}

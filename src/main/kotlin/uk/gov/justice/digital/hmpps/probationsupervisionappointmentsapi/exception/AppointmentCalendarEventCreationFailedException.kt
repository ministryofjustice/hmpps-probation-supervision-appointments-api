package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.exception

open class AppointmentCalendarEventCreationFailedException(message: String) : RuntimeException(message) {
  constructor(
    entity: String,
    field: String,
    value: Any,
  ) : this("$entity with $field of $value not found")
}

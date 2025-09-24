package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.repository

interface EventRepository {
  fun findByExternalRefAndOutlookId(externalRef: String, outlookId: String): Event?
  fun findByExternalRef(externalRef: String): Event?
  fun findByOutlookId(outlookId: String): Event?
}

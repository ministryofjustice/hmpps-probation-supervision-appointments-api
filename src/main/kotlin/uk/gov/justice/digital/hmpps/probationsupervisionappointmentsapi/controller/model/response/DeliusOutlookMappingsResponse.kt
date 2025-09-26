package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response

import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.DeliusOutlookMapping

data class DeliusOutlookMappingsResponse(
  val mappings: List<DeliusOutlookMapping>,
)

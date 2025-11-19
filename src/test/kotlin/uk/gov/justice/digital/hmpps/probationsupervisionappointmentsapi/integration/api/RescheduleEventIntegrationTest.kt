package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.EventResponse
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.DeliusOutlookMapping
import java.time.ZonedDateTime

class RescheduleEventIntegrationTest : IntegrationTestBase() {

  private val mockNewStartDatetime = "2025-11-16T15:03:29Z"
  private val mockNewEndDatetime = "2025-11-16T15:48:29Z"
  private val mockOriginalStartDatetime = "2025-11-10T10:00:00Z"
  private val mockDurationMinutes: Long = 45

  @Test
  fun `unauthorized status returned for reschedule`() {
    webTestClient.post().uri("/calendar/event/reschedule")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `test successful rescheduling of appointment from future to future date via delete and create`() {
    val fromEmail = "MPoP-Digital-Team@justice.gov.uk"
    val attendeeEmail = "attendee@example.com"
    val oldSupervisionAppointmentUrn = "urn:uk.gov:hmpps:manage-supervision-service:appointment:old-123"
    val newSupervisionAppointmentUrn = "urn:uk.gov:hmpps:manage-supervision-service:appointment:new-456"

    val oldOutlookId = "old-outlook-event-id-1"
    val newOutlookId = "new-outlook-event-id-2"

    // Use future dates for both old and new appointments
    val futureOldStartDatetime = ZonedDateTime.now().plusDays(1).toString()
    val futureNewStartDatetime = ZonedDateTime.now().plusDays(2).toString()
    val futureNewEndDatetime = ZonedDateTime.now().plusDays(2).plusMinutes(mockDurationMinutes.toLong()).toString()

    // Pre-populate the Mapping Repository for the OLD event
    deliusOutlookMappingRepository.save(
      DeliusOutlookMapping(
        supervisionAppointmentUrn = oldSupervisionAppointmentUrn,
        outlookId = oldOutlookId,
      ),
    )

    // Stub for getting the old event details (to check if it's in the future)
    stubGraphGetEvent(fromEmail, oldOutlookId, futureOldStartDatetime)
    // Stub for deleting the old event
    stubGraphDeleteEvent(fromEmail, oldOutlookId)
    // Stub for creating the new rescheduled event - make sure to include the attendee email
    stubGraphCreateRescheduledEvent(
      attendeeEmail,
      newOutlookId,
      futureNewStartDatetime,
      futureNewEndDatetime,
    )

    val requestBody = mapOf(
      "oldSupervisionAppointmentUrn" to oldSupervisionAppointmentUrn,
      "rescheduledEventRequest" to mapOf(
        "recipients" to listOf(mapOf("emailAddress" to attendeeEmail, "name" to "Attendee")),
        "message" to "Rescheduled meeting body",
        "subject" to "Rescheduled Meeting Subject",
        "start" to futureNewStartDatetime,
        "durationInMinutes" to mockDurationMinutes,
        "supervisionAppointmentUrn" to newSupervisionAppointmentUrn,
      ),
    )

    val expected = EventResponse(
      id = newOutlookId,
      subject = "Rescheduled Meeting Subject",
      startDate = futureNewStartDatetime,
      endDate = futureNewEndDatetime,
      attendees = listOf(attendeeEmail),
    )

    webTestClient.post().uri("/calendar/event/reschedule")
      .headers(setAuthorisation())
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(requestBody)
      .exchange()
      .expectStatus().isOk
      .expectBody(EventResponse::class.java)
      .isEqualTo(expected)

    // Verify the new mapping was created
    val newMapping = deliusOutlookMappingRepository.findBySupervisionAppointmentUrn(newSupervisionAppointmentUrn)
    assertThat(newMapping).isNotNull()
    assertThat(newMapping?.outlookId).isEqualTo(newOutlookId)
    assertThat(newMapping?.supervisionAppointmentUrn).isEqualTo(newSupervisionAppointmentUrn)
  }

  @Test
  fun `test successful rescheduling of appointment to a past date results in delete only`() {
    val fromEmail = "MPoP-Digital-Team@justice.gov.uk"
    val attendeeEmail = "attendee@example.com"
    val oldSupervisionAppointmentUrn = "urn:uk.gov:hmpps:manage-supervision-service:appointment:old-124"
    val newSupervisionAppointmentUrn = "urn:uk.gov:hmpps:manage-supervision-service:appointment:new-457"

    // 1. Define Event IDs and Dates
    val oldOutlookId = "old-outlook-event-id-3"
    val originalStartDateTime = ZonedDateTime.now().plusDays(5).withNano(0).toString()

    val pastStartDateTime = ZonedDateTime.now().minusDays(5).withNano(0)
    val newStartDateTime = pastStartDateTime.toString()

    // 2. Pre-populate the Mapping Repository for the OLD event
    deliusOutlookMappingRepository.save(
      DeliusOutlookMapping(
        supervisionAppointmentUrn = oldSupervisionAppointmentUrn,
        outlookId = oldOutlookId,
      ),
    )

    stubGraphGetEvent(fromEmail, oldOutlookId, originalStartDateTime)
    stubGraphDeleteEvent(fromEmail, oldOutlookId)

    val durationMinutes: Long = 45
    val expectedEndDateTime = pastStartDateTime.plusMinutes(durationMinutes).toString()
    val requestBody = mapOf(
      "oldSupervisionAppointmentUrn" to oldSupervisionAppointmentUrn,
      "rescheduledEventRequest" to mapOf(
        "recipients" to listOf(mapOf("emailAddress" to attendeeEmail, "name" to "Attendee")),
        "message" to "Rescheduled meeting body",
        "subject" to "Past Reschedule Subject",
        "start" to newStartDateTime,
        "durationInMinutes" to durationMinutes,
        "supervisionAppointmentUrn" to newSupervisionAppointmentUrn,
      ),
    )
    val expected = EventResponse(
      id = null,
      subject = "Past Reschedule Subject",
      startDate = newStartDateTime.substringBefore("["),
      endDate = expectedEndDateTime.substringBefore("["),
      attendees = listOf(attendeeEmail),
    )

    webTestClient.post().uri("/calendar/event/reschedule")
      .headers(setAuthorisation())
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(requestBody)
      .exchange()
      .expectStatus().isOk
      .expectBody(EventResponse::class.java)
      .isEqualTo(expected)

    val newResponse = deliusOutlookMappingRepository.findBySupervisionAppointmentUrn(newSupervisionAppointmentUrn)
    assertThat(newResponse).isNull()
  }
}

package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.EventResponse
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.DeliusOutlookMapping
import java.time.ZoneOffset
import java.time.ZonedDateTime

class RescheduleEventIntegrationTest : IntegrationTestBase() {

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

    val mockDurationMinutes: Long = 45

    // Future dates as ZonedDateTime (Jackson can parse)
    val futureOldStart = ZonedDateTime.now(ZoneOffset.UTC).plusDays(1)
    val futureNewStart = ZonedDateTime.now(ZoneOffset.UTC).plusDays(2)
    val futureNewEnd = futureNewStart.plusMinutes(mockDurationMinutes)

    // Pre-populate the mapping repository for the old event
    deliusOutlookMappingRepository.save(
      DeliusOutlookMapping(
        supervisionAppointmentUrn = oldSupervisionAppointmentUrn,
        outlookId = oldOutlookId,
      ),
    )

    stubGraphGetEvent(
      fromEmail,
      oldOutlookId,
      futureOldStart.toLocalDateTime().toString(),
    )
    stubGraphDeleteEvent(fromEmail, oldOutlookId)
    stubGraphCreateRescheduledEvent(
      attendeeEmail,
      newOutlookId,
      futureNewStart.toLocalDateTime().toString(),
      futureNewEnd.toLocalDateTime().toString(),
    )

    val requestBody = mapOf(
      "oldSupervisionAppointmentUrn" to oldSupervisionAppointmentUrn,
      "rescheduledEventRequest" to mapOf(
        "recipients" to listOf(mapOf("emailAddress" to attendeeEmail, "name" to "Attendee")),
        "message" to "Rescheduled meeting body",
        "subject" to "Rescheduled Meeting Subject",
        "start" to futureNewStart,
        "durationInMinutes" to mockDurationMinutes,
        "supervisionAppointmentUrn" to newSupervisionAppointmentUrn,
      ),
    )

    val expected = EventResponse(
      id = newOutlookId,
      subject = "Rescheduled Meeting Subject",
      startDate = futureNewStart.toLocalDateTime().toString(),
      endDate = futureNewEnd.toLocalDateTime().toString(),
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

    val oldOutlookId = "old-outlook-event-id-3"
    val durationMinutes: Long = 45

    val oldStart = ZonedDateTime.now(ZoneOffset.UTC).plusDays(5)

    deliusOutlookMappingRepository.save(
      DeliusOutlookMapping(
        supervisionAppointmentUrn = oldSupervisionAppointmentUrn,
        outlookId = oldOutlookId,
      ),
    )

    stubGraphGetEvent(fromEmail, oldOutlookId, oldStart.toLocalDateTime().toString())
    stubGraphDeleteEvent(fromEmail, oldOutlookId)

    // New start is in the past
    val pastStart = ZonedDateTime.now(ZoneOffset.UTC).minusDays(5)
    val pastEnd = pastStart.plusMinutes(durationMinutes)

    val requestBody = mapOf(
      "oldSupervisionAppointmentUrn" to oldSupervisionAppointmentUrn,
      "rescheduledEventRequest" to mapOf(
        "recipients" to listOf(mapOf("emailAddress" to attendeeEmail, "name" to "Attendee")),
        "message" to "Rescheduled meeting body",
        "subject" to "Past Reschedule Subject",
        "start" to pastStart, // ZonedDateTime, includes offset
        "durationInMinutes" to durationMinutes,
        "supervisionAppointmentUrn" to newSupervisionAppointmentUrn,
      ),
    )

    val expected = EventResponse(
      id = null,
      subject = "Past Reschedule Subject",
      startDate = pastStart.toString(), // includes Z offset
      endDate = pastEnd.toString(), // includes Z offset
      attendees = listOf(attendeeEmail),
    )

    // Perform the request
    webTestClient.post().uri("/calendar/event/reschedule")
      .headers(setAuthorisation())
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(requestBody)
      .exchange()
      .expectStatus().isOk
      .expectBody(EventResponse::class.java)
      .isEqualTo(expected)

    val newMapping = deliusOutlookMappingRepository.findBySupervisionAppointmentUrn(newSupervisionAppointmentUrn)
    assertThat(newMapping).isNull()
  }
}

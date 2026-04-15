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
    val fromEmail = "from@example.com"
    val attendeeEmail = "attendee@example.com"
    val oldSupervisionAppointmentUrn = "urn:uk.gov:hmpps:manage-supervision-service:appointment:old-123"
    val newSupervisionAppointmentUrn = "urn:uk.gov:hmpps:manage-supervision-service:appointment:new-456"

    val oldOutlookId = "old-outlook-event-id-1"
    val newOutlookId = "new-outlook-event-id-2"

    val duration = 45L

    val futureOldStart = ZonedDateTime.now(ZoneOffset.UTC).plusDays(1).withNano(0)
    val futureNewStart = ZonedDateTime.now(ZoneOffset.UTC).plusDays(2).withNano(0)
    val futureNewEnd = futureNewStart.plusMinutes(duration)

    deliusOutlookMappingRepository.save(
      DeliusOutlookMapping(supervisionAppointmentUrn = oldSupervisionAppointmentUrn, outlookId = oldOutlookId),
    )

    stubGraphGetEvent(
      fromEmail,
      oldOutlookId,
      futureOldStart.toOffsetDateTime().toString(),
    )

    stubGraphDeleteEvent(fromEmail, oldOutlookId)

    stubGraphCreateRescheduledEvent(
      attendeeEmail,
      newOutlookId,
      futureNewStart.toOffsetDateTime().toString(),
      futureNewEnd.toOffsetDateTime().toString(),
    )

    val requestBody = mapOf(
      "oldSupervisionAppointmentUrn" to oldSupervisionAppointmentUrn,
      "rescheduledEventRequest" to mapOf(
        "recipients" to listOf(mapOf("emailAddress" to attendeeEmail, "name" to "Attendee")),
        "message" to "Rescheduled meeting body",
        "subject" to "Rescheduled Meeting Subject",
        "start" to futureNewStart,
        "durationInMinutes" to duration,
        "supervisionAppointmentUrn" to newSupervisionAppointmentUrn,
      ),
    )

    val expected = EventResponse(
      id = newOutlookId,
      subject = "Rescheduled Meeting Subject",
      startDate = futureNewStart.toOffsetDateTime().toString(),
      endDate = futureNewEnd.toOffsetDateTime().toString(),
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

    val newMapping =
      deliusOutlookMappingRepository.findBySupervisionAppointmentUrn(newSupervisionAppointmentUrn)

    assertThat(newMapping).isNotNull()
    assertThat(newMapping?.outlookId).isEqualTo(newOutlookId)
  }

  @Test
  fun `test successful rescheduling of appointment to a past date results in delete only`() {
    val fromEmail = "from@example.com"
    val attendeeEmail = "attendee@example.com"
    val oldUrn = "urn:uk.gov:hmpps:manage-supervision-service:appointment:old-124"
    val newUrn = "urn:uk.gov:hmpps:manage-supervision-service:appointment:new-457"

    val oldOutlookId = "old-outlook-event-id-3"
    val duration = 45L

    val oldStart = ZonedDateTime.now(ZoneOffset.UTC)
      .plusDays(5)
      .withNano(0)

    deliusOutlookMappingRepository.save(
      DeliusOutlookMapping(supervisionAppointmentUrn = oldUrn, outlookId = oldOutlookId),
    )

    stubGraphGetEvent(
      fromEmail,
      oldOutlookId,
      oldStart.toLocalDateTime().withNano(0).toString(),
    )

    stubGraphDeleteEvent(fromEmail, oldOutlookId)

    val pastStart = ZonedDateTime.now(ZoneOffset.UTC)
      .minusDays(5)
      .withNano(0)

    val pastEnd = pastStart.plusMinutes(duration)

    val requestBody = mapOf(
      "oldSupervisionAppointmentUrn" to oldUrn,
      "rescheduledEventRequest" to mapOf(
        "recipients" to listOf(mapOf("emailAddress" to attendeeEmail, "name" to "Attendee")),
        "message" to "Rescheduled meeting body",
        "subject" to "Past Reschedule Subject",
        "start" to pastStart,
        "durationInMinutes" to duration,
        "supervisionAppointmentUrn" to newUrn,
      ),
    )

    val expected = EventResponse(
      id = null,
      subject = "Past Reschedule Subject",
      startDate = pastStart.toOffsetDateTime().toString(),
      endDate = pastEnd.toOffsetDateTime().toString(),
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

    val newMapping = deliusOutlookMappingRepository.findBySupervisionAppointmentUrn(newUrn)
    assertThat(newMapping).isNull()
  }
}

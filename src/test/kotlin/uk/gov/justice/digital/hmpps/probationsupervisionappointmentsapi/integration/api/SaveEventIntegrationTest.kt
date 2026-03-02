package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.EventResponse
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.IntegrationTestBase
import uk.gov.service.notify.NotificationClient
import uk.gov.service.notify.SendSmsResponse
import uk.gov.service.notify.Template
import java.util.UUID

class SaveEventIntegrationTest : IntegrationTestBase() {

  @MockitoBean
  private lateinit var notificationClient: NotificationClient

  @Test
  fun `unauthorized status returned`() {
    webTestClient.post().uri("/calendar/event")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @ParameterizedTest
  @CsvSource(
    "2044-09-16T10:00:00Z, 2044-09-16T10:30:00Z,mock-event-id-123",
    "2025-09-16T10:00Z, 2025-09-16T10:30Z,",
  )
  fun `test successful creation of event`(start: String, end: String, outlookId: String?) {
    val email = "test@test.com"
    stubGraphCreateEvent(email)

    val templateId = "7a3c8a69-30fb-4361-8a3b-125be59aeddf"
    val notificationId = UUID.randomUUID().toString()
    whenever(notificationClient.getTemplateById(templateId)).thenReturn(
      Template(
        """
        {
          "id": "$templateId",
          "name": "Appointment reminder",
          "type": "sms",
          "created_at": "2024-01-01T10:00:00Z",
          "updated_at": null,
          "version": 1,
          "body": "Reminder: Dear ((FIRST_NAME)). Appointment on ((APPOINTMENT_DATE)) at ((APPOINTMENT_TIME)).",
          "subject": null,
          "letter_contact_block": null,
          "personalisation": null
        }
        """.trimIndent(),
      ),
    )
    whenever(notificationClient.sendSms(anyString(), anyString(), any(), any())).thenReturn(
      SendSmsResponse(
        """
          {
            "id": "$notificationId",
            "reference": "X123456",
            "content": {
              "body": "Reminder: Dear John Doe. Appointment on Saturday 16 September at 10am.",
              "from_number": "447700900000"
            },
            "uri": "https://api.notifications.service.gov.uk/v2/notifications/$notificationId",
            "template": {
              "id": "$templateId",
              "version": 1,
              "uri": "https://api.notifications.service.gov.uk/v2/templates/$templateId"
            }
          }
        """.trimIndent(),
      ),
    )
    val durationMinutes: Long = 30
    val supervisionAppointmentUrn = "urn:uk:gov:hmpps:manage-supervision-service:appointment:8afbd895-c8e7-4a49-8eaa-3149243e7932"
    val smsRequest = mapOf(
      "smsOptIn" to true,
      "firstName" to "John Doe",
      "personTelephoneNumber" to "07123456789",
      "crn" to "X123456",
    )

    val body = mapOf(
      "fromEmail" to email,
      "recipients" to listOf(mapOf("emailAddress" to "test@test.com", "name" to "Attendee")),
      "message" to "Meeting with Jon",
      "subject" to "3 Way Meeting (Non NS) with Jon Smith",
      "start" to start,
      "durationInMinutes" to durationMinutes,
      "supervisionAppointmentUrn" to supervisionAppointmentUrn,
      "smsEventRequest" to smsRequest,
    )

    val expected = EventResponse(outlookId, "3 Way Meeting (Non NS) with Jon Smith", start, end, listOf("test@test.com"))
    webTestClient.post().uri("/calendar/event")
      .headers(setAuthorisation())
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(body)
      .exchange()
      .expectStatus().isCreated
      .expectBody(EventResponse::class.java)
      .isEqualTo(expected)

    val response = deliusOutlookMappingRepository.findBySupervisionAppointmentUrn(supervisionAppointmentUrn)

    assertEquals(body["supervisionAppointmentUrn"], response?.supervisionAppointmentUrn)
  }
}

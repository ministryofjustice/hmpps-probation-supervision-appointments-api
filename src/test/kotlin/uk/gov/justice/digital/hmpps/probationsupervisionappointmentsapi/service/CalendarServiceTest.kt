package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import com.microsoft.graph.models.Attendee
import com.microsoft.graph.models.BodyType
import com.microsoft.graph.models.DateTimeTimeZone
import com.microsoft.graph.models.EmailAddress
import com.microsoft.graph.models.Event
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertThrows
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.EventRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.Recipient
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.DeliusOutlookMappingsResponse
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.EventResponse
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.exception.NotFoundException
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.DeliusOutlookMapping
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.DeliusOutlookMappingRepository
import java.time.Instant
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertTrue

class CalendarServiceTest {

  private val mappingRepository: DeliusOutlookMappingRepository = mockk()
  private val microsoftGraphCalendarClient: MicrosoftGraphCalendarClient = mockk()
  private lateinit var calendarService: CalendarService


  @BeforeEach
  fun setup() {
    calendarService = CalendarService(
      microsoftGraphCalendarClient = microsoftGraphCalendarClient,
      deliusOutlookMappingRepository = mappingRepository,
      fromEmail = "calendar@example.com",
    )
  }


  @Test
  fun `getEventDetailsMappings should return correct mapping response`() {
    // Given
    val urn = "urn:supervision:abc123"

    val createdAt = Instant.parse("2025-10-10T09:00:00Z")
    val updatedAt = Instant.parse("2025-10-11T11:30:00Z")

    val mockMapping = DeliusOutlookMapping(
      supervisionAppointmentUrn = urn,
      outlookId = "outlook-event-456",
    ).apply {
      this.createdAt = createdAt
      this.updatedAt = updatedAt
    }

    every { mappingRepository.findBySupervisionAppointmentUrn(any()) } returns mockMapping

    // When
    val result: DeliusOutlookMappingsResponse = calendarService.getEventDetailsMappings(urn)

    // Then
    assertEquals(urn, result.supervisionAppointmentUrn)
    assertEquals("outlook-event-456", result.outlookId)
    assertEquals(createdAt.toString(), result.createdAt)
    assertEquals(updatedAt.toString(), result.updatedAt)

    verify(exactly = 1) { mappingRepository.findBySupervisionAppointmentUrn(urn) }
  }

  @Test
  fun `getEventDetailsMappings should throw NotFoundException when mapping not found`() {
    // Given
    val urn = "-6456b5ba5b954fd6"
    every { mappingRepository.findBySupervisionAppointmentUrn(urn) } returns null

    // When + Then
    val exception = assertThrows<NotFoundException> {
      calendarService.getEventDetailsMappings(urn)
    }

    assertEquals("DeliusOutlookMapping with supervisionAppointmentUrn of $urn not found", exception.message)
  }

  @Test
  fun `toEventResponse transforms Event to EventResponse correctly`() {
    // Given
    val graphEvent = Event().apply {
      id = "event123"
      subject = "Supervision Meeting"
      start = DateTimeTimeZone().apply {
        dateTime = "2025-10-17T10:00:00"
        timeZone = "Europe/London"
      }
      end = DateTimeTimeZone().apply {
        dateTime = "2025-10-17T10:30:00"
        timeZone = "Europe/London"
      }
      attendees = listOf(
        Attendee().apply {
          emailAddress = EmailAddress().apply {
            address = "officer@example.com"
          }
        },
        Attendee().apply {
          emailAddress = EmailAddress().apply {
            address = "probationer@example.com"
          }
        },
      )
    }

    // When
    val response: EventResponse = graphEvent.toEventResponse()

    // Then
    assertEquals("event123", response.id)
    assertEquals("Supervision Meeting", response.subject)
    assertEquals("2025-10-17T10:00:00", response.startDate)
    assertEquals("2025-10-17T10:30:00", response.endDate)
    assertEquals(listOf("officer@example.com", "probationer@example.com"), response.attendees)
  }


  @Test
  fun `event build successful`() {

    val startDateTime = LocalDateTime.of(2025, 10, 20, 14, 0)
    val eventRequest = EventRequest(
      recipients = listOf(Recipient("test@example.com", "Test User")),
      message = "<p>Hello</p>",
      subject = "Test Event",
      start = startDateTime,
      duration = 30,
      supervisionAppointmentUrn = "urn:supervision:test-123"
    )

    val buildEvent = calendarService.buildEvent(eventRequest)

    assertEquals("Test Event", buildEvent.subject)
    assertEquals(startDateTime.toString(), buildEvent.start.dateTime)
    assertEquals("Europe/London", buildEvent.start.timeZone)
    assertEquals(startDateTime.plusMinutes(30).toString(), buildEvent.end.dateTime)
    assertEquals("Europe/London", buildEvent.end.timeZone)

    // Attendee assertions
    assertNotNull(buildEvent.attendees)

    assertTrue(buildEvent.attendees.map { it.emailAddress.address }.contains("test@example.com"))
    assertTrue(buildEvent.attendees.map { it.emailAddress.name }.contains("Test User"))

    // Body assertions
    assertNotNull(buildEvent.body)
    assertEquals(BodyType.Html, buildEvent.body.contentType)
    assertEquals("<p>Hello</p>", buildEvent.body.content)
  }

  @Test
  fun `toDeliusOutlookMappingsResponse transforms mapping correctly`() {
    // Given

    val createdAt = Instant.parse("2025-10-10T09:00:00Z")
    val updatedAt = Instant.parse("2025-10-11T11:30:00Z")

    val mapping = DeliusOutlookMapping(
      supervisionAppointmentUrn = "urn:appointment:abc123",
      outlookId = "outlook-xyz789",
    ).apply {
      this.createdAt = createdAt
      this.updatedAt = updatedAt
    }

    // When
    val response: DeliusOutlookMappingsResponse = mapping.toDeliusOutlookMappingsResponse()

    // Then
    assertEquals("urn:appointment:abc123", response.supervisionAppointmentUrn)
    assertEquals("outlook-xyz789", response.outlookId)
    assertEquals(createdAt.toString(), response.createdAt)
    assertEquals(updatedAt.toString(), response.updatedAt)
  }
}

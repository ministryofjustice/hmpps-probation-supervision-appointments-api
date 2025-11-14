package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import com.microsoft.graph.models.BodyType
import com.microsoft.graph.models.Event
import com.microsoft.graph.serviceclient.GraphServiceClient
import com.microsoft.graph.users.UsersRequestBuilder
import com.microsoft.graph.users.item.UserItemRequestBuilder
import com.microsoft.graph.users.item.calendar.CalendarRequestBuilder
import com.microsoft.graph.users.item.calendar.events.EventsRequestBuilder
import com.microsoft.graph.users.item.calendar.events.item.EventItemRequestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.lenient
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.EventRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.Recipient
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.RescheduleEventRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.DeliusOutlookMapping
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.DeliusOutlookMappingRepository
import java.time.ZonedDateTime
import java.util.UUID

private const val EVENT_TIMEZONE = "Europe/London"

@ExtendWith(MockitoExtension::class)
class CalendarServiceTest {

  @Mock
  private lateinit var graphClient: GraphServiceClient

  @Mock
  private lateinit var deliusOutlookMappingRepository: DeliusOutlookMappingRepository

  @Mock
  private lateinit var usersRequestBuilder: UsersRequestBuilder

  @Mock
  private lateinit var userItemRequestBuilder: UserItemRequestBuilder

  @Mock
  private lateinit var calendarRequestBuilder: CalendarRequestBuilder

  @Mock
  private lateinit var eventsRequestBuilder: EventsRequestBuilder

  @Mock
  private lateinit var eventItemRequestBuilder: EventItemRequestBuilder

  @Captor
  private lateinit var mappingCaptor: ArgumentCaptor<DeliusOutlookMapping>
  private val fromEmail = "sender@justice.gov.uk"
  private lateinit var calendarService: CalendarService

  private val fixedStartDateTime: ZonedDateTime = ZonedDateTime.parse("2050-01-01T10:00:00Z")
  private val durationMinutes: Long = 45

  private val mockRecipient = Recipient("attendee@example.com", "Attendee Name")
  private val mockEventRequest = EventRequest(
    recipients = listOf(mockRecipient),
    message = "Test Message Body",
    subject = "Test Subject",
    start = fixedStartDateTime,
    durationInMinutes = durationMinutes,
    supervisionAppointmentUrn = "urn:test:123",
  )

  @BeforeEach
  fun setup() {
    calendarService = CalendarService(graphClient, deliusOutlookMappingRepository, fromEmail)
  }

  private fun setupGraphMocks() {
    lenient().`when`(graphClient.users()).thenReturn(usersRequestBuilder)
    lenient().`when`(usersRequestBuilder.byUserId(anyString())).thenReturn(userItemRequestBuilder)
    lenient().`when`(userItemRequestBuilder.calendar()).thenReturn(calendarRequestBuilder)
    lenient().`when`(calendarRequestBuilder.events()).thenReturn(eventsRequestBuilder)
    lenient().`when`(eventsRequestBuilder.byEventId(anyString())).thenReturn(eventItemRequestBuilder)
  }

  @Nested
  inner class SendEventTests {
    private val outlookId = "mock-outlook-id"

    @BeforeEach
    fun setupNested() {
      setupGraphMocks()
    }

    @Test
    fun `should create event via Graph and save mapping to repository`() {
      val fixedEndDateTime = fixedStartDateTime.plusMinutes(durationMinutes)

      // Mock the Event that the Graph POST call returns (ensuring non-null fields are present)
      val mockGraphEventResponse = Event().apply {
        id = outlookId
        subject = mockEventRequest.subject
        start = com.microsoft.graph.models.DateTimeTimeZone().apply { dateTime = fixedStartDateTime.toString() }
        end = com.microsoft.graph.models.DateTimeTimeZone().apply { dateTime = fixedEndDateTime.toString() }
        attendees = listOf(
          com.microsoft.graph.models.Attendee().apply {
            emailAddress = com.microsoft.graph.models.EmailAddress().apply { address = mockRecipient.emailAddress }
          },
        )
      }

      `when`(eventsRequestBuilder.post(any(Event::class.java))).thenReturn(mockGraphEventResponse)

      calendarService.sendEvent(mockEventRequest)

      verify(deliusOutlookMappingRepository, times(1)).save(mappingCaptor.capture())
      assertEquals(mockEventRequest.supervisionAppointmentUrn, mappingCaptor.value.supervisionAppointmentUrn)
      assertEquals(outlookId, mappingCaptor.value.outlookId)
    }
  }

  @Nested
  inner class RescheduleEventTests {
    private val oldUrn = "urn:old:456"
    private val newUrn = "urn:new:789"
    private val oldOutlookId = "old-outlook-id"
    private val mockMapping = DeliusOutlookMapping(supervisionAppointmentUrn = oldUrn, outlookId = oldOutlookId)

    @BeforeEach
    fun setupNested() {
      setupGraphMocks()
    }

    @Test
    fun `should delete old event and send new event if new start is in the future`() {
      val usersBuilder = mock(UsersRequestBuilder::class.java)
      val userItemBuilder = mock(UserItemRequestBuilder::class.java)

      val eventsBuilder = mock(EventsRequestBuilder::class.java)
      val eventItemRequestBuilder = mock(EventItemRequestBuilder::class.java)

      val futureEventRequest = mockEventRequest.copy(supervisionAppointmentUrn = newUrn)
      val rescheduleRequest = RescheduleEventRequest(futureEventRequest, oldUrn)
      val futureEndDateTime = futureEventRequest.start.plusMinutes(durationMinutes)

      `when`(deliusOutlookMappingRepository.findBySupervisionAppointmentUrn(oldUrn)).thenReturn(mockMapping)

      val newOutlookId = "new-outlook-id-123"
      val mockGraphEventResponse = Event().apply {
        id = newOutlookId
        subject = futureEventRequest.subject
        start = com.microsoft.graph.models.DateTimeTimeZone().apply { dateTime = futureEventRequest.start.toString() }
        end = com.microsoft.graph.models.DateTimeTimeZone().apply { dateTime = futureEndDateTime.toString() }
        attendees = listOf(
          com.microsoft.graph.models.Attendee().apply {
            emailAddress = com.microsoft.graph.models.EmailAddress()
              .apply { address = futureEventRequest.recipients.first().emailAddress }
          },
        )
      }
      `when`(eventsRequestBuilder.post(any(Event::class.java))).thenReturn(mockGraphEventResponse)

      `when`(deliusOutlookMappingRepository.save(any(DeliusOutlookMapping::class.java)))
        .thenReturn(DeliusOutlookMapping(supervisionAppointmentUrn = newUrn, outlookId = newOutlookId))

      calendarService.rescheduleEvent(rescheduleRequest)

      verify(eventsRequestBuilder, times(1)).byEventId(oldOutlookId)
      verify(eventsRequestBuilder, times(1)).post(any(Event::class.java))

      verify(deliusOutlookMappingRepository, times(1)).save(mappingCaptor.capture())
      assertEquals(newUrn, mappingCaptor.value.supervisionAppointmentUrn)
      assertEquals(newOutlookId, mappingCaptor.value.outlookId)
    }

    @Test
    fun `should only delete old event and return manual response if new start is in the past`() {
      val pastStart = ZonedDateTime.now().minusDays(1)
      val pastEnd = pastStart.plusMinutes(durationMinutes)

      val pastEventRequest = mockEventRequest.copy(start = pastStart, supervisionAppointmentUrn = newUrn)
      val rescheduleRequest = RescheduleEventRequest(pastEventRequest, oldUrn)

      `when`(deliusOutlookMappingRepository.findBySupervisionAppointmentUrn(oldUrn)).thenReturn(mockMapping)
      doNothing().`when`(eventItemRequestBuilder).delete()

      val result = calendarService.rescheduleEvent(rescheduleRequest)

      verify(eventsRequestBuilder, times(1)).byEventId(oldOutlookId)
      verify(eventItemRequestBuilder, times(1)).delete()
      verify(eventsRequestBuilder, never()).post(any(Event::class.java))
      verify(deliusOutlookMappingRepository, never()).save(any(DeliusOutlookMapping::class.java))
    }

    @Test
    fun `should throw exception and not call post if delete fails and new start is in the future`() {
      // Arrange
      val service = spy(calendarService)

      val futureEventRequest = mockEventRequest.copy(supervisionAppointmentUrn = newUrn)
      val rescheduleRequest = RescheduleEventRequest(futureEventRequest, oldUrn)
      val expectedException = RuntimeException("Graph API error: Delete failed")

      // Only stub what WILL be called
      doThrow(expectedException)
        .`when`(service)
        .deleteExistingOutlookEvent(oldUrn)

      // Act + Assert
      assertThrows<RuntimeException> {
        service.rescheduleEvent(rescheduleRequest)
      }

      // Verify delete attempted
      verify(service).deleteExistingOutlookEvent(oldUrn)

      // Verify no follow-up calls
      verify(eventsRequestBuilder, never()).post(any())
      verify(deliusOutlookMappingRepository, never()).save(any())
    }
  }

  @Nested
  inner class HelperMethodTests {

    @Test
    fun `buildEvent should correctly map request to MS Graph Event object`() {
      val event = calendarService.buildEvent(mockEventRequest)

      assertEquals(mockEventRequest.subject, event.subject)
      assertEquals(EVENT_TIMEZONE, event.start.timeZone)
      assertEquals(fixedStartDateTime.toString(), event.start.dateTime)
      assertEquals(fixedStartDateTime.plusMinutes(durationMinutes).toString(), event.end.dateTime)
      assertEquals(1, event.attendees.size)
      assertEquals(mockRecipient.emailAddress, event.attendees.first().emailAddress.address)
      assertEquals(mockEventRequest.message, event.body.content)
      assertEquals(BodyType.Html, event.body.contentType)
    }

    @Test
    fun `toEventResponse should correctly map MS Graph Event to EventResponse`() {
      val mockId = UUID.randomUUID().toString()
      val mockSubject = "Mock Subject"
      val mockStart = "2024-10-10T11:00:00Z"
      val mockEnd = "2024-10-10T12:00:00Z"
      val mockAttendeeAddress = "mock@example.com"

      val graphEvent = Event().apply {
        id = mockId
        subject = mockSubject
        start = com.microsoft.graph.models.DateTimeTimeZone().apply { dateTime = mockStart }
        end = com.microsoft.graph.models.DateTimeTimeZone().apply { dateTime = mockEnd }
        attendees = listOf(
          com.microsoft.graph.models.Attendee().apply {
            emailAddress = com.microsoft.graph.models.EmailAddress().apply { address = mockAttendeeAddress }
          },
        )
      }

      val response = graphEvent.toEventResponse()

      assertEquals(mockId, response.id)
      assertEquals(mockSubject, response.subject)
      assertEquals(mockStart, response.startDate)
      assertEquals(mockEnd, response.endDate)
      assertEquals(listOf(mockAttendeeAddress), response.attendees)
    }
  }
}

package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import com.microsoft.graph.models.Attendee
import com.microsoft.graph.models.BodyType
import com.microsoft.graph.models.DateTimeTimeZone
import com.microsoft.graph.models.EmailAddress
import com.microsoft.graph.models.Event
import com.microsoft.graph.serviceclient.GraphServiceClient
import com.microsoft.graph.users.UsersRequestBuilder
import com.microsoft.graph.users.item.UserItemRequestBuilder
import com.microsoft.graph.users.item.calendar.CalendarRequestBuilder
import com.microsoft.graph.users.item.calendar.events.EventsRequestBuilder
import com.microsoft.graph.users.item.calendar.events.item.EventItemRequestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.EventRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.Recipient
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.RescheduleEventRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.SmsEventRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.DeliusOutlookMapping
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.DeliusOutlookMappingRepository
import uk.gov.service.notify.NotificationClient
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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

  @Mock
  private lateinit var featureFlags: FeatureFlagsService

  @Mock
  private lateinit var notificationClient: NotificationClient

  @Mock
  private lateinit var telemetryService: TelemetryService

  @Captor
  private lateinit var mappingCaptor: ArgumentCaptor<DeliusOutlookMapping>

  private val fromEmail = "MPoP-Digital-Team@justice.gov.uk"
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
    calendarService = CalendarService(graphClient, deliusOutlookMappingRepository, featureFlags, notificationClient, telemetryService, fromEmail, "template-id-1")
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

      whenever(eventsRequestBuilder.post(any(Event::class.java))).thenReturn(mockGraphEventResponse)
      whenever(deliusOutlookMappingRepository.save(any(DeliusOutlookMapping::class.java)))
        .thenAnswer { it.arguments[0] as DeliusOutlookMapping }

      val result = calendarService.sendEvent(mockEventRequest)

      verify(eventsRequestBuilder, times(1)).post(any(Event::class.java))
      verify(deliusOutlookMappingRepository, times(1)).save(mappingCaptor.capture())

      assertEquals(mockEventRequest.supervisionAppointmentUrn, mappingCaptor.value.supervisionAppointmentUrn)
      assertEquals(outlookId, mappingCaptor.value.outlookId)
      assertEquals(outlookId, result?.id)
      assertEquals(mockEventRequest.subject, result?.subject)
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
      val futureEventRequest = mockEventRequest.copy(supervisionAppointmentUrn = newUrn)
      val rescheduleRequest = RescheduleEventRequest(futureEventRequest, oldUrn)
      val futureEndDateTime = futureEventRequest.start.plusMinutes(durationMinutes)

      // Mock getting the old event details for deletion
      whenever(deliusOutlookMappingRepository.findBySupervisionAppointmentUrn(oldUrn))
        .thenReturn(mockMapping)

      val oldEventForGet = Event().apply {
        id = oldOutlookId
        subject = "Old Subject"
        start = DateTimeTimeZone().apply {
          dateTime = futureEventRequest.start.toLocalDateTime().toString()
        }
        end = DateTimeTimeZone().apply {
          dateTime = futureEndDateTime.toLocalDateTime().toString()
        }
        attendees = listOf()
      }

      whenever(eventItemRequestBuilder.get(any())).thenReturn(oldEventForGet)
      doNothing().`when`(eventItemRequestBuilder).delete()

      // Mock creating the new event
      val newOutlookId = "new-outlook-id-123"
      val mockGraphEventResponse = Event().apply {
        id = newOutlookId
        subject = futureEventRequest.subject
        start = DateTimeTimeZone().apply {
          dateTime = futureEventRequest.start.toLocalDateTime().toString()
        }
        end = DateTimeTimeZone().apply {
          dateTime = futureEndDateTime.toLocalDateTime().toString()
        }
        attendees = listOf(
          Attendee().apply {
            emailAddress = EmailAddress()
              .apply { address = futureEventRequest.recipients.first().emailAddress }
          },
        )
      }

      whenever(eventsRequestBuilder.post(any(Event::class.java))).thenReturn(mockGraphEventResponse)
      whenever(deliusOutlookMappingRepository.save(any(DeliusOutlookMapping::class.java)))
        .thenAnswer { it.arguments[0] as DeliusOutlookMapping }

      val result = calendarService.rescheduleEvent(rescheduleRequest)

      // Verify deletion happened
      verify(eventItemRequestBuilder, times(1)).delete()

      // Verify new event created
      verify(eventsRequestBuilder, times(1)).post(any(Event::class.java))
      verify(deliusOutlookMappingRepository, times(1)).save(mappingCaptor.capture())

      assertEquals(newUrn, mappingCaptor.value.supervisionAppointmentUrn)
      assertEquals(newOutlookId, mappingCaptor.value.outlookId)
      assertNotNull(result)
      assertEquals(newOutlookId, result?.id)
    }

    @Test
    fun `should only delete old event and return manual response if new start is in the past`() {
      val pastStart = ZonedDateTime.now().minusDays(1)
      val pastEventRequest = EventRequest(
        recipients = listOf(mockRecipient),
        message = "Test Message Body",
        subject = "Test Subject",
        start = pastStart,
        durationInMinutes = durationMinutes,
        supervisionAppointmentUrn = newUrn,
      )
      val rescheduleRequest = RescheduleEventRequest(pastEventRequest, oldUrn)

      // Mock the old event (which is in the future, so it will be deleted)
      whenever(deliusOutlookMappingRepository.findBySupervisionAppointmentUrn(oldUrn))
        .thenReturn(mockMapping)

      val futureOldEvent = Event().apply {
        id = oldOutlookId
        subject = "Old Subject"
        start = com.microsoft.graph.models.DateTimeTimeZone().apply {
          dateTime = ZonedDateTime.now().plusDays(1).toLocalDateTime().toString()
        }
        end = com.microsoft.graph.models.DateTimeTimeZone().apply {
          dateTime = ZonedDateTime.now().plusDays(1).plusMinutes(durationMinutes)
            .toLocalDateTime().toString()
        }
        attendees = listOf()
      }
      whenever(eventItemRequestBuilder.get(any())).thenReturn(futureOldEvent)
      doNothing().`when`(eventItemRequestBuilder).delete()

      val result = calendarService.rescheduleEvent(rescheduleRequest)

      // Old event should be deleted
      verify(eventItemRequestBuilder, times(1)).delete()

      // No new event should be created (because new start is in past)
      verify(eventsRequestBuilder, never()).post(any(Event::class.java))
      verify(deliusOutlookMappingRepository, never()).save(any(DeliusOutlookMapping::class.java))

      // Should return a response with null ID (manual entry)
      assertNotNull(result)
      assertNull(result?.id)
      assertEquals(pastEventRequest.subject, result?.subject)
    }

    @Test
    fun `should throw exception and not call post if delete fails and new start is in the future`() {
      val service = spy(calendarService)
      val futureEventRequest = mockEventRequest.copy(supervisionAppointmentUrn = newUrn)
      val rescheduleRequest = RescheduleEventRequest(futureEventRequest, oldUrn)
      val expectedException = RuntimeException("Graph API error: Delete failed")

      doThrow(expectedException)
        .`when`(service)
        .deleteExistingOutlookEvent(oldUrn)

      assertThrows<RuntimeException> {
        service.rescheduleEvent(rescheduleRequest)
      }

      verify(service).deleteExistingOutlookEvent(oldUrn)
      verify(eventsRequestBuilder, never()).post(any())
      verify(deliusOutlookMappingRepository, never()).save(any())
    }

    @Test
    fun `should not delete old event if it no longer exists in repository`() {
      val service = spy(calendarService)

      // Mock getEventDetails to return null (mapping doesn't exist)
      doReturn(null).`when`(service).getEventDetails(oldUrn)

      // Should not throw - getEventDetails returns null when mapping doesn't exist
      service.deleteExistingOutlookEvent(oldUrn)

      verify(eventItemRequestBuilder, never()).delete()
    }

    @Test
    fun `should not delete old event if start time is in the past`() {
      val service = spy(calendarService)

      val pastEvent = uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.EventResponse(
        id = oldOutlookId,
        subject = "Past Event",
        startDate = LocalDateTime.now().minusDays(1).toString(),
        endDate = LocalDateTime.now().minusDays(1).plusMinutes(durationMinutes).toString(),
        attendees = listOf(),
      )

      doReturn(pastEvent).`when`(service).getEventDetails(oldUrn)

      service.deleteExistingOutlookEvent(oldUrn)

      verify(eventItemRequestBuilder, never()).delete()
    }

    @Test
    fun `event without sms request`() {
      val eventRequestWithoutSms = mockEventRequest.copy(smsEventRequest = null)
      val event = mockEvent()

      whenever(deliusOutlookMappingRepository.save(any(DeliusOutlookMapping::class.java)))
        .thenAnswer { it.arguments[0] as DeliusOutlookMapping }

      val response = calendarService.sendEvent(eventRequestWithoutSms)

      verify(notificationClient, never()).sendSms(
        anyString(),
        anyString(),
        any(),
        anyString(),
      )

      verifyNoInteractions(telemetryService)

      assertEquals(event.toEventResponse(), response)
    }

    @Test
    fun `should track telemetry and capture exception if sendSms fails`() {
      val eventRequest = mockEventRequest
        .copy(smsEventRequest = SmsEventRequest("name", "mobile", "crn", true))
      val exception = RuntimeException("SMS failure")

      whenever(featureFlags.enabled("sms-notification-toggle")).thenReturn(true)
      doThrow(exception).`when`(notificationClient).sendSms(
        anyString(),
        anyString(),
        any(),
        anyString(),
      )

      val event = mockEvent()

      val response = calendarService.sendEvent(eventRequest)

      val templateValues = mapOf(
        "FirstName" to "name",
        "NextWorkSession" to eventRequest.start.format(DateTimeFormatter.ofPattern("d MMMM yyyy 'at' h:mma")),
      )
      verify(notificationClient).sendSms(
        "template-id-1",
        "mobile",
        templateValues,
        "crn",
      )

      verify(telemetryService)
        .trackEvent(
          "AppointmentReminderFailure",
          mapOf("crn" to "crn", "supervisionAppointmentUrn" to eventRequest.supervisionAppointmentUrn),
        )

      verify(telemetryService)
        .trackException(
          exception,
          mapOf("crn" to "crn", "supervisionAppointmentUrn" to eventRequest.supervisionAppointmentUrn),
        )

      assertEquals(event.toEventResponse(), response)
    }
  }

  private fun mockEvent(): Event {
    val event = Event().apply {
      id = "some-id"
    }

    whenever(
      graphClient
        .users()
        .byUserId(anyString())
        .calendar()
        .events()
        .post(any()),
    ).thenReturn(event)
    return event
  }

  @Nested
  inner class HelperMethodTests {

    @Test
    fun `buildEvent should correctly map request to MS Graph Event object`() {
      val event = calendarService.buildEvent(mockEventRequest)

      assertEquals(mockEventRequest.subject, event.subject)
      assertEquals(EVENT_TIMEZONE, event.start?.timeZone)
      assertEquals(fixedStartDateTime.toString(), event.start?.dateTime)
      assertEquals(fixedStartDateTime.plusMinutes(durationMinutes).toString(), event.end?.dateTime)
      assertEquals(1, event.attendees?.size)
      assertEquals(mockRecipient.emailAddress, event.attendees?.first()?.emailAddress?.address)
      assertEquals(mockEventRequest.message, event.body?.content)
      assertEquals(BodyType.Html, event.body?.contentType)
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

    @Test
    fun `getAttendees should map multiple recipients correctly`() {
      val recipients = listOf(
        Recipient("user1@example.com", "User One"),
        Recipient("user2@example.com", "User Two"),
      )

      val attendees = calendarService.getAttendees(recipients)

      assertEquals(2, attendees.size)
      assertEquals("user1@example.com", attendees[0].emailAddress?.address)
      assertEquals("User One", attendees[0].emailAddress?.name)
      assertEquals("user2@example.com", attendees[1].emailAddress?.address)
      assertEquals("User Two", attendees[1].emailAddress?.name)
    }
  }
}

package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.config.SmsLanguage
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.EventRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.Recipient
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.SmsEventRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.SmsResponse
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.NotificationMapping
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.NotificationMappingRepository
import uk.gov.service.notify.NotificationClient
import uk.gov.service.notify.SendSmsResponse
import uk.gov.service.notify.Template
import java.time.ZonedDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class CalendarV2ServiceTest {
  @Mock
  private lateinit var notificationMappingRepository: NotificationMappingRepository

  @Mock
  private lateinit var notificationClient: NotificationClient

  @Mock
  private lateinit var telemetryService: TelemetryService

  @Mock
  private lateinit var smsTemplateResolverService: SmsTemplateResolverService

  private lateinit var calendarV2Service: CalendarV2Service

  private val recipient = Recipient("attendee@example.com", "Attendee Name")
  private val smsEventRequest = SmsEventRequest(
    firstName = "Alex",
    mobileNumber = "07700900000",
    crn = "X123456",
    smsOptIn = true,
    practitionerFirstName = "Sam",
  )
  private val eventRequest = EventRequest(
    recipients = listOf(recipient),
    message = "Test Message Body",
    subject = "Test Subject",
    start = ZonedDateTime.parse("2050-01-01T10:00:00Z"),
    durationInMinutes = 45,
    supervisionAppointmentUrn = "urn:test:123",
    smsEventRequest = smsEventRequest,
  )

  @BeforeEach
  fun setup() {
    calendarV2Service = CalendarV2Service(
      notificationMappingRepository,
      notificationClient,
      telemetryService,
      smsTemplateResolverService,
    )
  }

  @Test
  fun `should send English and Welsh SMS using new templates and leave contact publication to delivery processing`() {
    val appointmentTypeCode = "COAP"
    val englishNotificationId = stubSms(SmsLanguage.ENGLISH, appointmentTypeCode)
    val welshNotificationId = stubSms(SmsLanguage.WELSH, appointmentTypeCode)

    val result = calendarV2Service.sendEvent(
      eventRequest.copy(
        recipients = emptyList(),
        smsEventRequest = smsEventRequest.copy(includeWelshTranslation = true, appointmentTypeCode = appointmentTypeCode),
      ),
    )

    assertNull(result.id)
    assertEquals(SmsResponse(englishNotificationId, welshNotificationId), result.smsResponse)
    verify(smsTemplateResolverService).getNewAppointmentTemplate(SmsLanguage.ENGLISH, appointmentTypeCode)
    verify(smsTemplateResolverService).getNewAppointmentTemplate(SmsLanguage.WELSH, appointmentTypeCode)
    val personalisation = argumentCaptor<Map<String, String>>()
    verify(notificationClient, times(2)).sendSms(any(), eq("07700900000"), personalisation.capture(), eq("X123456"))
    assertEquals(
      listOf(
        mapOf(
          "FIRST_NAME" to "Alex",
          "PRACTITIONER_FIRST_NAME" to "Sam",
          "APPOINTMENT_DATE" to "Saturday 1 January",
          "APPOINTMENT_TIME" to "10am",
          "APPOINTMENT_TYPE" to "an office appointment",
        ),
        mapOf(
          "FIRST_NAME" to "Alex",
          "PRACTITIONER_FIRST_NAME" to "Sam",
          "APPOINTMENT_DATE" to "Dydd Sadwrn 1 Ionawr",
          "APPOINTMENT_TIME" to "10am",
          "APPOINTMENT_TYPE" to "apwyntiad yn y swyddfa",
        ),
      ),
      personalisation.allValues,
    )
    val mappings = argumentCaptor<NotificationMapping>()
    verify(notificationMappingRepository, times(2)).save(mappings.capture())
    assertEquals(listOf(englishNotificationId, welshNotificationId), mappings.allValues.map { it.notificationId })
    mappings.allValues.forEach {
      assertEquals(eventRequest.supervisionAppointmentUrn, it.deliusExternalReference)
      assertEquals("X123456", it.crn)
      assertNull(it.contactEventPublishedAt)
    }
    verify(notificationMappingRepository, never()).markContactEventPublished(any(), any())
    verify(smsTemplateResolverService, never()).getLegacyTemplate(any(), org.mockito.kotlin.anyOrNull())
  }

  @Test
  fun `should track past appointment without sending SMS or creating an Outlook event`() {
    val pastRequest = eventRequest.copy(start = ZonedDateTime.now().minusDays(1))

    val result = calendarV2Service.sendEvent(pastRequest)

    assertNull(result.id)
    assertNull(result.smsResponse)
    assertEquals(pastRequest.subject, result.subject)
    assertEquals(pastRequest.start.toString(), result.startDate)
    verify(telemetryService).trackEvent(
      "AppointmentInThePast",
      mapOf("supervisionAppointmentUrn" to pastRequest.supervisionAppointmentUrn),
    )
    verifyNoInteractions(notificationClient, notificationMappingRepository)
  }

  @Test
  fun `should return null SMS response when opted out without creating an Outlook event`() {
    val result = calendarV2Service.sendEvent(eventRequest.copy(smsEventRequest = smsEventRequest.copy(smsOptIn = false)))

    assertNull(result.id)
    assertNull(result.smsResponse)
    assertEquals(eventRequest.subject, result.subject)
    verifyNoInteractions(notificationClient, notificationMappingRepository, smsTemplateResolverService)
  }

  private fun stubSms(language: SmsLanguage, appointmentTypeCode: String): UUID {
    val templateId = UUID.randomUUID()
    val notificationId = UUID.randomUUID()
    val template = mock<Template>()
    whenever(template.id).thenReturn(templateId)
    whenever(smsTemplateResolverService.getNewAppointmentTemplate(language, appointmentTypeCode)).thenReturn(template)
    val notifyResponse = mock<SendSmsResponse>()
    whenever(notifyResponse.notificationId).thenReturn(notificationId)
    whenever(notifyResponse.templateId).thenReturn(templateId)
    whenever(notifyResponse.body).thenReturn("$language appointment reminder")
    whenever(notificationClient.sendSms(eq(templateId.toString()), any(), any(), any())).thenReturn(notifyResponse)
    return notificationId
  }
}

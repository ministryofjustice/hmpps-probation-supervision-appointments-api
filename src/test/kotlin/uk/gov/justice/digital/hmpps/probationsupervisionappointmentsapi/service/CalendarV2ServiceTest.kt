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
  private lateinit var featureFlags: FeatureFlagsService

  @Mock
  private lateinit var notificationClient: NotificationClient

  @Mock
  private lateinit var telemetryService: TelemetryService

  @Mock
  private lateinit var smsTemplateResolverService: SmsTemplateResolverService

  @Mock
  private lateinit var domainEventService: DomainEventService

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
      featureFlags,
      notificationClient,
      telemetryService,
      smsTemplateResolverService,
      domainEventService,
    )
  }

  @Test
  fun `should send English and Welsh SMS using new templates without checking the template flag`() {
    whenever(featureFlags.isEnabledForUser("sms-notification-toggle", recipient.emailAddress)).thenReturn(true)
    val englishNotificationId = stubSms(SmsLanguage.ENGLISH)
    val welshNotificationId = stubSms(SmsLanguage.WELSH)
    whenever(notificationMappingRepository.markContactEventPublished(eq(englishNotificationId), any())).thenReturn(1)
    whenever(notificationMappingRepository.markContactEventPublished(eq(welshNotificationId), any())).thenReturn(1)

    val result = calendarV2Service.sendEvent(
      eventRequest.copy(smsEventRequest = smsEventRequest.copy(includeWelshTranslation = true)),
    )

    assertNull(result.id)
    assertEquals(SmsResponse(englishNotificationId, welshNotificationId), result.smsResponse)
    val personalisation = argumentCaptor<Map<String, String>>()
    verify(notificationClient, times(2)).sendSms(any(), eq("07700900000"), personalisation.capture(), eq("X123456"))
    personalisation.allValues.forEach { assertEquals("Sam", it["PRACTITIONER_FIRST_NAME"]) }
    val mappings = argumentCaptor<NotificationMapping>()
    verify(notificationMappingRepository, times(2)).save(mappings.capture())
    assertEquals(listOf(englishNotificationId, welshNotificationId), mappings.allValues.map { it.notificationId })
    mappings.allValues.forEach {
      assertEquals(eventRequest.supervisionAppointmentUrn, it.deliusExternalReference)
      assertEquals("X123456", it.crn)
      verify(domainEventService).buildAndPublishContactEvent("X123456", it.notificationId)
    }
    verify(featureFlags, never()).isEnabledForUser(eq("new-sms-appointment-template"), any())
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
    verifyNoInteractions(notificationClient, notificationMappingRepository, featureFlags, domainEventService)
  }

  @Test
  fun `should return null SMS response when SMS feature flag is disabled without creating an Outlook event`() {
    whenever(featureFlags.isEnabledForUser("sms-notification-toggle", recipient.emailAddress)).thenReturn(false)

    val result = calendarV2Service.sendEvent(eventRequest)

    assertNull(result.id)
    assertNull(result.smsResponse)
    assertEquals(eventRequest.subject, result.subject)
    verifyNoInteractions(notificationClient, notificationMappingRepository, smsTemplateResolverService)
    verify(featureFlags, never()).isEnabledForUser(eq("new-sms-appointment-template"), any())
  }

  private fun stubSms(language: SmsLanguage): UUID {
    val templateId = UUID.randomUUID()
    val notificationId = UUID.randomUUID()
    val template = mock<Template>()
    whenever(template.id).thenReturn(templateId)
    whenever(smsTemplateResolverService.getNewAppointmentTemplate(language, null)).thenReturn(template)
    val notifyResponse = mock<SendSmsResponse>()
    whenever(notifyResponse.notificationId).thenReturn(notificationId)
    whenever(notifyResponse.templateId).thenReturn(templateId)
    whenever(notifyResponse.body).thenReturn("$language appointment reminder")
    whenever(notificationClient.sendSms(eq(templateId.toString()), any(), any(), any())).thenReturn(notifyResponse)
    return notificationId
  }
}

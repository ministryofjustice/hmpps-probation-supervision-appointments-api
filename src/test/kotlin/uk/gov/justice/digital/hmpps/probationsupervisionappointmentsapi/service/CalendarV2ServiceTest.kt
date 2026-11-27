package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import io.sentry.Sentry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.Mockito.mockStatic
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.dao.DataAccessResourceFailureException
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.config.SmsLanguage
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.EventRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.Recipient
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.request.SmsEventRequest
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.controller.model.response.SmsResponse
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.exception.NotFoundException
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.NotificationMapping
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.NotificationMappingRepository
import uk.gov.service.notify.NotificationClient
import uk.gov.service.notify.NotificationClientException
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
      notificationClient,
      telemetryService,
      smsTemplateResolverService,
      domainEventService,
    )
  }

  @Test
  fun `should send English and Welsh SMS using new templates and leave contact publication to delivery processing`() {
    val appointmentTypeCode = "COAP"
    val englishNotificationId = stubSms(SmsLanguage.ENGLISH, appointmentTypeCode)
    val welshNotificationId = stubSms(SmsLanguage.WELSH, appointmentTypeCode)

    val request = eventRequest.copy(
      recipients = emptyList(),
      smsEventRequest = smsEventRequest.copy(includeWelshTranslation = true, appointmentTypeCode = appointmentTypeCode),
    )

    val result = calendarV2Service.sendEvent(request)

    assertNull(result.id)
    assertEquals(SmsResponse(englishNotificationId, welshNotificationId), result.smsResponse)
    verify(smsTemplateResolverService).getNewAppointmentTemplate(SmsLanguage.ENGLISH, appointmentTypeCode)
    verify(smsTemplateResolverService).getNewAppointmentTemplate(SmsLanguage.WELSH, appointmentTypeCode)
    val personalisation = argumentCaptor<Map<String, String>>()

    inOrder(notificationClient, domainEventService) {
      verify(notificationClient, times(2)).sendSms(any(), eq("07700900000"), personalisation.capture(), eq("X123456"))
      verify(domainEventService).buildAndPublishOutlookCalendarRequest(request)
    }

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
  fun `should track past appointment without sending SMS or publishing an Outlook event`() {
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
    verifyNoInteractions(notificationClient, notificationMappingRepository, domainEventService)
  }

  @Test
  fun `should return null SMS response when opted out and still publish Outlook calendar request`() {
    val optedOutRequest = eventRequest.copy(smsEventRequest = smsEventRequest.copy(smsOptIn = false))

    val result = calendarV2Service.sendEvent(optedOutRequest)

    assertNull(result.id)
    assertNull(result.smsResponse)
    assertEquals(optedOutRequest.subject, result.subject)
    verifyNoInteractions(notificationClient, notificationMappingRepository, smsTemplateResolverService)
    verify(domainEventService).buildAndPublishOutlookCalendarRequest(optedOutRequest)
  }

  @Test
  fun `should return null SMS response when SMS details are absent and still publish Outlook calendar request`() {
    val requestWithoutSms = eventRequest.copy(smsEventRequest = null)

    val result = calendarV2Service.sendEvent(requestWithoutSms)

    assertNull(result.id)
    assertNull(result.smsResponse)
    assertEquals(requestWithoutSms.subject, result.subject)
    verifyNoInteractions(notificationClient, notificationMappingRepository, smsTemplateResolverService)
    verify(domainEventService).buildAndPublishOutlookCalendarRequest(requestWithoutSms)
  }

  @Test
  fun `should propagate exception when Outlook calendar request publication fails`() {
    val requestWithoutSms = eventRequest.copy(smsEventRequest = null)
    val exception = RuntimeException("SNS publication failed")
    doThrow(exception).whenever(domainEventService).buildAndPublishOutlookCalendarRequest(requestWithoutSms)

    val thrown = assertThrows<RuntimeException> {
      calendarV2Service.sendEvent(requestWithoutSms)
    }

    assertEquals("SNS publication failed", thrown.message)
  }

  @ParameterizedTest
  @EnumSource(SmsLanguage::class)
  fun `should isolate template resolution failure to the affected language`(failedLanguage: SmsLanguage) {
    val successfulLanguage = if (failedLanguage == SmsLanguage.ENGLISH) SmsLanguage.WELSH else SmsLanguage.ENGLISH
    val successfulNotificationId = stubSms(successfulLanguage, "COAP")
    val exception = NotFoundException("Template not found")
    whenever(smsTemplateResolverService.getNewAppointmentTemplate(failedLanguage, "COAP")).thenThrow(exception)

    assertPartialSmsSuccess(failedLanguage, successfulNotificationId, exception)

    verify(notificationClient).sendSms(any(), eq("07700900000"), any(), eq("X123456"))
    verify(notificationMappingRepository).save(any())
  }

  @ParameterizedTest
  @EnumSource(SmsLanguage::class)
  fun `should isolate Notify failure to the affected language`(failedLanguage: SmsLanguage) {
    val successfulLanguage = if (failedLanguage == SmsLanguage.ENGLISH) SmsLanguage.WELSH else SmsLanguage.ENGLISH
    val successfulNotificationId = stubSms(successfulLanguage, "COAP")
    val failedTemplateId = UUID.randomUUID()
    val template = mock<Template>()
    whenever(template.id).thenReturn(failedTemplateId)
    whenever(smsTemplateResolverService.getNewAppointmentTemplate(failedLanguage, "COAP")).thenReturn(template)
    val exception = NotificationClientException("SMS failure")
    doThrow(exception).whenever(notificationClient).sendSms(eq(failedTemplateId.toString()), any(), any(), any())

    assertPartialSmsSuccess(failedLanguage, successfulNotificationId, exception)

    verify(notificationClient, times(2)).sendSms(any(), eq("07700900000"), any(), eq("X123456"))
    verify(notificationMappingRepository).save(any())
  }

  @ParameterizedTest
  @EnumSource(SmsLanguage::class)
  fun `should isolate mapping save failure to the affected language`(failedLanguage: SmsLanguage) {
    val successfulLanguage = if (failedLanguage == SmsLanguage.ENGLISH) SmsLanguage.WELSH else SmsLanguage.ENGLISH
    val successfulNotificationId = stubSms(successfulLanguage, "COAP")
    val failedNotificationId = stubSms(failedLanguage, "COAP")
    val exception = DataAccessResourceFailureException("Mapping save failed")
    doAnswer { it.getArgument<NotificationMapping>(0) }.whenever(notificationMappingRepository).save(any())
    doThrow(exception).whenever(notificationMappingRepository).save(
      argThat<NotificationMapping> { notificationId == failedNotificationId },
    )

    assertPartialSmsSuccess(failedLanguage, successfulNotificationId, exception)

    verify(notificationClient, times(2)).sendSms(any(), eq("07700900000"), any(), eq("X123456"))
    verify(notificationMappingRepository, times(2)).save(any())
  }

  private fun assertPartialSmsSuccess(failedLanguage: SmsLanguage, successfulNotificationId: UUID, exception: Exception) {
    val request = eventRequest.copy(
      smsEventRequest = smsEventRequest.copy(includeWelshTranslation = true, appointmentTypeCode = "COAP"),
    )

    mockStatic(Sentry::class.java).use { sentryMock ->
      val result = assertDoesNotThrow { calendarV2Service.sendEvent(request) }

      assertNull(result.id)
      val expectedSmsResponse = if (failedLanguage == SmsLanguage.ENGLISH) {
        SmsResponse(null, successfulNotificationId)
      } else {
        SmsResponse(successfulNotificationId, null)
      }
      assertEquals(expectedSmsResponse, result.smsResponse)
      sentryMock.verify { Sentry.captureException(exception) }
      sentryMock.verifyNoMoreInteractions()
    }

    verify(smsTemplateResolverService).getNewAppointmentTemplate(SmsLanguage.ENGLISH, "COAP")
    verify(smsTemplateResolverService).getNewAppointmentTemplate(SmsLanguage.WELSH, "COAP")
    val successfulMapping = argumentCaptor<NotificationMapping>()
    verify(notificationMappingRepository).save(
      argThat<NotificationMapping> { notificationId == successfulNotificationId },
    )
    verify(notificationMappingRepository, org.mockito.kotlin.atLeastOnce()).save(successfulMapping.capture())
    val mapping = successfulMapping.allValues.single { it.notificationId == successfulNotificationId }
    assertEquals(eventRequest.supervisionAppointmentUrn, mapping.deliusExternalReference)
    assertEquals(smsEventRequest.crn, mapping.crn)
    assertNull(mapping.contactEventPublishedAt)
    verify(notificationMappingRepository, never()).markContactEventPublished(any(), any())
    verify(domainEventService).buildAndPublishOutlookCalendarRequest(request)
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

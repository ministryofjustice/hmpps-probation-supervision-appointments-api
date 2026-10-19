package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.NotificationMappingRepository
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class NotifyDeliveryStatusServiceTest {

  @Mock
  private lateinit var notificationMappingRepository: NotificationMappingRepository

  @Mock
  private lateinit var domainEventService: DomainEventService

  @Mock
  private lateinit var featureFlagsService: FeatureFlagsService

  @Mock
  private lateinit var telemetryService: TelemetryService

  private lateinit var service: NotifyDeliveryStatusService

  private val notificationId = UUID.randomUUID()
  private val crn = "X123456"
  private val createdAt = Instant.now()

  @BeforeEach
  fun setUp() {
    service = NotifyDeliveryStatusService(notificationMappingRepository, domainEventService, featureFlagsService, telemetryService)
  }

  @Nested
  inner class Delivered {
    @Test
    fun `day 1 - flag disabled - non-historical record - records status only, no mark, no publish`() {
      whenever(notificationMappingRepository.updateStatus(eq(notificationId), eq("delivered"), any(), any())).thenReturn(1)
      whenever(featureFlagsService.isEnabled(CONTACT_EVENT_ON_CONFIRMED_DELIVERY_FLAG)).thenReturn(false)

      val updated = service.recordStatus(notificationId, crn, "delivered", createdAt = createdAt)

      assertEquals(1, updated)
      verify(notificationMappingRepository, never()).markContactEventPublished(any(), any())
      verifyNoInteractions(domainEventService)
    }

    @Test
    fun `flag enabled - records delivered status and publishes contact event`() {
      whenever(notificationMappingRepository.updateStatus(eq(notificationId), eq("delivered"), any(), any())).thenReturn(1)
      whenever(featureFlagsService.isEnabled(CONTACT_EVENT_ON_CONFIRMED_DELIVERY_FLAG)).thenReturn(true)
      whenever(notificationMappingRepository.markContactEventPublished(eq(notificationId), any())).thenReturn(1)

      val updated = service.recordStatus(notificationId, crn, "delivered", createdAt = createdAt)

      assertEquals(1, updated)
      verify(domainEventService).buildAndPublishContactEvent(crn = crn, notificationId = notificationId)
    }

    @Test
    fun `flag is enabled but the guard reports the row was already claimed - does not publish again`() {
      // Simulates the send-time path (CalendarService) having already won the atomic guard -
      // e.g. the flag was false when the SMS was sent, then flipped to true before delivery.
      whenever(notificationMappingRepository.updateStatus(eq(notificationId), eq("delivered"), any(), any())).thenReturn(1)
      whenever(featureFlagsService.isEnabled(CONTACT_EVENT_ON_CONFIRMED_DELIVERY_FLAG)).thenReturn(true)
      whenever(notificationMappingRepository.markContactEventPublished(eq(notificationId), any())).thenReturn(0)

      val updated = service.recordStatus(notificationId, crn, "delivered", createdAt = createdAt)

      assertEquals(1, updated)
      verifyNoInteractions(domainEventService)
    }

    @Test
    fun `crn is null - records status but skips publish without throwing`() {
      whenever(notificationMappingRepository.updateStatus(eq(notificationId), eq("delivered"), any(), any())).thenReturn(1)

      val updated = service.recordStatus(notificationId, null, "delivered", createdAt = createdAt)

      assertEquals(1, updated)
      verifyNoInteractions(domainEventService)
    }

    @Test
    fun `publish fails - tracks telemetry and sentry, then rethrows`() {
      whenever(notificationMappingRepository.updateStatus(eq(notificationId), eq("delivered"), any(), any())).thenReturn(1)
      whenever(featureFlagsService.isEnabled(CONTACT_EVENT_ON_CONFIRMED_DELIVERY_FLAG)).thenReturn(true)
      whenever(notificationMappingRepository.markContactEventPublished(eq(notificationId), any())).thenReturn(1)
      val publishFailure = RuntimeException("SNS unavailable")
      whenever(domainEventService.buildAndPublishContactEvent(crn = crn, notificationId = notificationId)).thenThrow(publishFailure)

      mockStatic(io.sentry.Sentry::class.java).use { sentryMock ->
        assertThrows<RuntimeException> { service.recordStatus(notificationId, crn, "delivered", createdAt = createdAt) }

        verify(telemetryService).trackEvent(
          eq("ContactEventPublishRolledBack"),
          eq(mapOf("notificationId" to notificationId.toString(), "crn" to crn)),
          any(),
        )
        verify(telemetryService).trackException(
          eq(publishFailure),
          eq(mapOf("notificationId" to notificationId.toString())),
          any(),
        )
        sentryMock.verify { io.sentry.Sentry.captureException(publishFailure) }
      }
    }

    @Test
    fun `update is a no-op - does not publish contact event even when flag is enabled`() {
      whenever(notificationMappingRepository.updateStatus(eq(notificationId), eq("delivered"), any(), any())).thenReturn(0)

      val updated = service.recordStatus(notificationId, crn, "delivered", createdAt = createdAt)

      assertEquals(0, updated)
      verifyNoInteractions(domainEventService, featureFlagsService)
    }
  }

  @Nested
  inner class NonDeliveredStatus {
    @Test
    fun `flag disabled - records a failure status, does not publish`() {
      whenever(notificationMappingRepository.updateStatus(eq(notificationId), eq("permanent-failure"), any(), any())).thenReturn(1)
      whenever(featureFlagsService.isEnabled(CONTACT_EVENT_ON_CONFIRMED_DELIVERY_FLAG)).thenReturn(false)

      val updated = service.recordStatus(notificationId, crn, "permanent-failure", createdAt = createdAt)

      assertEquals(1, updated)
      verify(notificationMappingRepository, never()).markContactEventPublished(any(), any())
      verifyNoInteractions(domainEventService)
    }

    @ParameterizedTest
    @ValueSource(strings = ["permanent-failure", "temporary-failure", "technical-failure"])
    fun `flag enabled - a final failure status also publishes the sms-sent contact event`(status: String) {
      whenever(notificationMappingRepository.updateStatus(eq(notificationId), eq(status), any(), any())).thenReturn(1)
      whenever(featureFlagsService.isEnabled(CONTACT_EVENT_ON_CONFIRMED_DELIVERY_FLAG)).thenReturn(true)
      whenever(notificationMappingRepository.markContactEventPublished(eq(notificationId), any())).thenReturn(1)

      val updated = service.recordStatus(notificationId, crn, status, createdAt = createdAt)

      assertEquals(1, updated)
      verify(domainEventService).buildAndPublishContactEvent(crn = crn, notificationId = notificationId)
    }
  }

  @Nested
  inner class HistoricalRecord {
    @ParameterizedTest
    @ValueSource(strings = ["delivered", "permanent-failure", "temporary-failure", "technical-failure"])
    fun `any final status - marks contactEventPublishedAt using its created date, does not publish`(status: String) {
      val historicalCreatedAt = java.time.Instant.parse("2024-01-01T00:00:00Z")
      whenever(notificationMappingRepository.updateStatus(eq(notificationId), eq(status), any(), any())).thenReturn(1)

      val updated = service.recordStatus(notificationId, crn, status, alreadyPublishedAt = historicalCreatedAt, createdAt = historicalCreatedAt)

      assertEquals(1, updated)
      verify(notificationMappingRepository).markContactEventPublished(notificationId, historicalCreatedAt)
      verifyNoInteractions(domainEventService)
    }
  }
}

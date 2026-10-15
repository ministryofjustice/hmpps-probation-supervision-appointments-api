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
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.NotificationMappingRepository
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

  @BeforeEach
  fun setUp() {
    service = NotifyDeliveryStatusService(notificationMappingRepository, domainEventService, featureFlagsService, telemetryService)
  }

  @Nested
  inner class Delivered {
    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `records delivered status, publishes contact event only when flag enabled`(flagEnabled: Boolean) {
      whenever(notificationMappingRepository.updateStatus(eq(notificationId), eq("delivered"), any(), any())).thenReturn(1)
      whenever(featureFlagsService.isEnabled(CONTACT_EVENT_ON_CONFIRMED_DELIVERY_FLAG)).thenReturn(flagEnabled)
      if (flagEnabled) {
        whenever(notificationMappingRepository.markContactEventPublished(eq(notificationId), any())).thenReturn(1)
      }

      val updated = service.recordStatus(notificationId, crn, "delivered")

      assertEquals(1, updated)
      if (flagEnabled) {
        verify(domainEventService).buildAndPublishContactEvent(crn = crn, notificationId = notificationId)
      } else {
        verifyNoInteractions(domainEventService)
      }
    }

    @Test
    fun `flag is enabled but the guard reports the row was already claimed - does not publish again`() {
      // Simulates the send-time path (CalendarService) having already won the atomic guard -
      // e.g. the flag was false when the SMS was sent, then flipped to true before delivery.
      whenever(notificationMappingRepository.updateStatus(eq(notificationId), eq("delivered"), any(), any())).thenReturn(1)
      whenever(featureFlagsService.isEnabled(CONTACT_EVENT_ON_CONFIRMED_DELIVERY_FLAG)).thenReturn(true)
      whenever(notificationMappingRepository.markContactEventPublished(eq(notificationId), any())).thenReturn(0)

      val updated = service.recordStatus(notificationId, crn, "delivered")

      assertEquals(1, updated)
      verifyNoInteractions(domainEventService)
    }

    @Test
    fun `crn is null - records status but skips publish without throwing`() {
      whenever(notificationMappingRepository.updateStatus(eq(notificationId), eq("delivered"), any(), any())).thenReturn(1)

      val updated = service.recordStatus(notificationId, null, "delivered")

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
        assertThrows<RuntimeException> { service.recordStatus(notificationId, crn, "delivered") }

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

      val updated = service.recordStatus(notificationId, crn, "delivered")

      assertEquals(0, updated)
      verifyNoInteractions(domainEventService, featureFlagsService)
    }
  }

  @Nested
  inner class NonDeliveredStatus {
    @Test
    fun `records a non-delivered terminal status - never publishes a contact event`() {
      whenever(notificationMappingRepository.updateStatus(eq(notificationId), eq("permanent-failure"), any(), any())).thenReturn(1)

      val updated = service.recordStatus(notificationId, crn, "permanent-failure")

      assertEquals(1, updated)
      verifyNoInteractions(domainEventService, featureFlagsService)
    }
  }
}

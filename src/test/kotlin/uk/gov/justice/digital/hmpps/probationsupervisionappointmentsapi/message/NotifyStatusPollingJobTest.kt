package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.NotificationMapping
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.NotificationMappingRepository
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.FeatureFlagsService
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.TelemetryService
import java.util.UUID
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class NotifyStatusPollingJobTest {

  @Mock
  private lateinit var notificationMappingRepository: NotificationMappingRepository

  @Mock
  private lateinit var domainEventPublisher: DomainEventPublisher

  @Mock
  private lateinit var telemetryService: TelemetryService

  @Mock
  private lateinit var featureFlagsService: FeatureFlagsService

  private lateinit var job: NotifyStatusPollingJob
  private val batchSize = 100

  @BeforeEach
  fun setUp() {
    job = NotifyStatusPollingJob(
      notificationMappingRepository,
      domainEventPublisher,
      telemetryService,
      featureFlagsService,
      batchSize,
    )
  }

  private fun mapping(notificationId: UUID = UUID.randomUUID(), deliusExternalReference: String = "delius-ref-1") = NotificationMapping(
    deliusExternalReference = deliusExternalReference,
    notificationId = notificationId,
    templateId = UUID.randomUUID(),
    message = "body",
    status = null,
  )

  @Nested
  inner class FlagDisabled {
    @Test
    fun `does nothing - does not query for unresolved rows or publish anything`() {
      whenever(featureFlagsService.isEnabled("notify-status-sweep-toggle")).thenReturn(false)

      job.poll()

      verifyNoInteractions(notificationMappingRepository, domainEventPublisher, telemetryService)
    }
  }

  @Nested
  inner class FlagEnabled {
    @Test
    fun `no unresolved rows - tracks telemetry with a zero count and publishes nothing`() {
      whenever(featureFlagsService.isEnabled("notify-status-sweep-toggle")).thenReturn(true)
      whenever(notificationMappingRepository.findUnresolved(limit = batchSize)).thenReturn(emptyList())

      job.poll()

      verify(telemetryService).trackEvent(eq("NotifyStatusPollingStarted"), eq(mapOf("unresolvedCount" to "0")), any())
      verifyNoInteractions(domainEventPublisher)
    }

    @Test
    fun `queries unresolved rows bounded by the configured batch size`() {
      whenever(featureFlagsService.isEnabled("notify-status-sweep-toggle")).thenReturn(true)
      whenever(notificationMappingRepository.findUnresolved(limit = batchSize)).thenReturn(emptyList())

      job.poll()

      verify(notificationMappingRepository).findUnresolved(limit = batchSize)
    }

    @Test
    fun `unresolved rows found - republishes a status-check-requested event per row`() {
      whenever(featureFlagsService.isEnabled("notify-status-sweep-toggle")).thenReturn(true)
      val first = mapping()
      val second = mapping()
      whenever(notificationMappingRepository.findUnresolved(limit = batchSize)).thenReturn(listOf(first, second))

      job.poll()

      verify(telemetryService).trackEvent(eq("NotifyStatusPollingStarted"), eq(mapOf("unresolvedCount" to "2")), any())
      val eventCaptor = argumentCaptor<HmppsDomainEvent>()
      verify(domainEventPublisher, times(2)).publish(eventCaptor.capture())

      val publishedNotificationIds = eventCaptor.allValues.map { it.additionalInformation["notificationId"] }
      assertEquals(
        listOf(first.notificationId.toString(), second.notificationId.toString()),
        publishedNotificationIds,
      )
      eventCaptor.allValues.forEach {
        assertEquals("probation.appointment.sms-status-check-requested", it.eventType)
      }
    }

    @Test
    fun `publishing fails for one row - error is tracked, and remaining rows still publish`() {
      whenever(featureFlagsService.isEnabled("notify-status-sweep-toggle")).thenReturn(true)
      val first = mapping()
      val second = mapping()
      whenever(notificationMappingRepository.findUnresolved(limit = batchSize)).thenReturn(listOf(first, second))
      val failure = RuntimeException("SNS unavailable")
      doThrow(failure).doNothing().whenever(domainEventPublisher).publish(any())

      job.poll()

      // The failing row does not fail the polling job - the second row is still attempted,
      // and the failure is tracked for alerting rather than swallowed.
      verify(domainEventPublisher, times(2)).publish(any())
      verify(telemetryService).trackException(eq(failure), eq(mapOf("notificationId" to first.notificationId.toString())), any())
    }
  }
}

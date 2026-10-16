package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.message

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.NotificationMapping
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.NotificationMappingRepository
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.NotifyDeliveryStatusService
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.TelemetryService
import uk.gov.service.notify.Notification
import uk.gov.service.notify.NotificationClient
import uk.gov.service.notify.NotificationClientException
import java.time.ZonedDateTime
import java.util.UUID

private const val CRN = "X123456"

@ExtendWith(MockitoExtension::class)
class NotifyStatusCheckHandlerTest {

  @Mock
  private lateinit var notificationMappingRepository: NotificationMappingRepository

  @Mock
  private lateinit var notificationClient: NotificationClient

  @Mock
  private lateinit var telemetryService: TelemetryService

  @Mock
  private lateinit var notifyDeliveryStatusService: NotifyDeliveryStatusService

  private lateinit var handler: NotifyStatusCheckHandler

  private val notificationId = UUID.randomUUID()
  private val deliusExternalReference = "delius-ref-1"

  @BeforeEach
  fun setUp() {
    handler = NotifyStatusCheckHandler(
      notificationMappingRepository,
      notificationClient,
      telemetryService,
      notifyDeliveryStatusService,
    )
  }

  private fun event(notificationId: UUID = this.notificationId) = HmppsDomainEvent(
    eventType = "probation.appointment.sms-status-check-requested",
    version = 1,
    detailUrl = null,
    occurredAt = ZonedDateTime.now(),
    description = "Check SMS delivery status",
    additionalInformation = mapOf("notificationId" to notificationId.toString()),
    personReference = null,
  )

  private fun mapping(status: String? = null, crn: String? = CRN) = NotificationMapping(
    deliusExternalReference = deliusExternalReference,
    notificationId = notificationId,
    templateId = UUID.randomUUID(),
    message = "body",
    status = status,
    crn = crn,
  )

  private fun notification(status: String): Notification {
    val notification = org.mockito.Mockito.mock(Notification::class.java)
    whenever(notification.status).thenReturn(status)
    return notification
  }

  private fun notifyException(httpResult: Int): NotificationClientException {
    val exception = org.mockito.Mockito.mock(NotificationClientException::class.java)
    whenever(exception.httpResult).thenReturn(httpResult)
    return exception
  }

  @Nested
  inner class OrphanMessages {
    @Test
    fun `no mapping found - tracks telemetry and does not call Notify`() {
      whenever(notificationMappingRepository.findByNotificationId(notificationId)).thenReturn(null)

      handler.handle(event())

      verify(telemetryService).trackEvent(
        eq("NotifyStatusCheckOrphanMessage"),
        eq(mapOf("notificationId" to notificationId.toString())),
        any(),
      )
      verifyNoInteractions(notificationClient)
    }
  }

  @Nested
  inner class AlreadyResolved {
    @Test
    fun `mapping already has a final status - no-op, Notify not called`() {
      whenever(notificationMappingRepository.findByNotificationId(notificationId)).thenReturn(mapping(status = "delivered"))

      handler.handle(event())

      verifyNoInteractions(notificationClient)
    }

    @Test
    fun `mapping has a pending status - still calls Notify to check for progress`() {
      whenever(notificationMappingRepository.findByNotificationId(notificationId)).thenReturn(mapping(status = "sending"))
      val delivered = notification("delivered")
      whenever(notificationClient.getNotificationById(notificationId.toString())).thenReturn(delivered)

      handler.handle(event())

      verify(notifyDeliveryStatusService).recordStatus(notificationId, CRN, "delivered")
    }
  }

  @Nested
  inner class PendingNotifyStatus {
    @ParameterizedTest
    @ValueSource(strings = ["created", "sending", "pending"])
    fun `Notify reports a non-final status - records it but is not treated as a failure`(status: String) {
      whenever(notificationMappingRepository.findByNotificationId(notificationId)).thenReturn(mapping())
      val nonfinal = notification(status)
      whenever(notificationClient.getNotificationById(notificationId.toString())).thenReturn(nonfinal)

      handler.handle(event())

      verify(notifyDeliveryStatusService).recordStatus(notificationId, CRN, status)
      verifyNoInteractions(telemetryService)
    }
  }

  @Nested
  inner class FinalNotifyStatus {
    @Test
    fun `Notify reports delivered - delegates to NotifyDeliveryStatusService, no failure telemetry`() {
      whenever(notificationMappingRepository.findByNotificationId(notificationId)).thenReturn(mapping(crn = CRN))
      val delivered = notification("delivered")
      whenever(notificationClient.getNotificationById(notificationId.toString())).thenReturn(delivered)
      whenever(notifyDeliveryStatusService.recordStatus(notificationId, CRN, "delivered")).thenReturn(1)

      handler.handle(event())

      verify(notifyDeliveryStatusService).recordStatus(notificationId, CRN, "delivered")
      verifyNoInteractions(telemetryService)
    }

    @Test
    fun `mapping has no crn but Notify holds the reference - falls back to Notify's reference, marks already published`() {
      val historicalMapping = mapping(crn = null)
      whenever(notificationMappingRepository.findByNotificationId(notificationId)).thenReturn(historicalMapping)
      val delivered = notification("delivered")
      whenever(delivered.reference).thenReturn(java.util.Optional.of(CRN))
      whenever(notificationClient.getNotificationById(notificationId.toString())).thenReturn(delivered)
      whenever(notifyDeliveryStatusService.recordStatus(notificationId, CRN, "delivered", historicalMapping.createdAt)).thenReturn(1)

      handler.handle(event())

      // Historical records without a CRN use Notify's reference.
      // As the contact event was already published when the SMS was sent,
      // pass the mapping's createdAt to prevent duplicate publication.
      verify(notifyDeliveryStatusService).recordStatus(notificationId, CRN, "delivered", historicalMapping.createdAt)
    }

    @Test
    fun `Notify reports permanent-failure - records status and raises telemetry`() {
      whenever(notificationMappingRepository.findByNotificationId(notificationId)).thenReturn(mapping())
      val permanentFailure = notification("permanent-failure")
      whenever(notificationClient.getNotificationById(notificationId.toString())).thenReturn(permanentFailure)
      whenever(notifyDeliveryStatusService.recordStatus(notificationId, CRN, "permanent-failure")).thenReturn(1)

      handler.handle(event())

      verify(notifyDeliveryStatusService).recordStatus(notificationId, CRN, "permanent-failure")
      verify(telemetryService).trackEvent(
        eq("SmsDeliveryFailed"),
        eq(
          mapOf(
            "notificationId" to notificationId.toString(),
            "deliusExternalReference" to deliusExternalReference,
            "status" to "permanent-failure",
          ),
        ),
        any(),
      )
    }

    @Test
    fun `a concurrent delivery already recorded a final status - update is a no-op, no duplicate telemetry`() {
      whenever(notificationMappingRepository.findByNotificationId(notificationId)).thenReturn(mapping())
      val permanentFailure = notification("permanent-failure")
      whenever(notificationClient.getNotificationById(notificationId.toString())).thenReturn(permanentFailure)
      // Another handler already transitioned the row to a final status first, so the
      // conditional update affects zero rows.
      whenever(notifyDeliveryStatusService.recordStatus(notificationId, CRN, "permanent-failure")).thenReturn(0)

      handler.handle(event())

      verify(notifyDeliveryStatusService).recordStatus(notificationId, CRN, "permanent-failure")
      verifyNoInteractions(telemetryService)
    }
  }

  @Nested
  inner class NotifyNotFound {
    @Test
    fun `Notify returns 404 - records not-found status, tracks telemetry, does not rethrow`() {
      whenever(notificationMappingRepository.findByNotificationId(notificationId)).thenReturn(mapping())
      val notFound = notifyException(404)
      whenever(notificationClient.getNotificationById(notificationId.toString())).thenThrow(notFound)
      whenever(notificationMappingRepository.updateStatus(eq(notificationId), eq("not-found"), any(), any())).thenReturn(1)

      handler.handle(event())

      verify(notificationMappingRepository).updateStatus(eq(notificationId), eq("not-found"), any(), any())
      verify(telemetryService).trackEvent(
        eq("NotifyStatusCheckNotFound"),
        eq(mapOf("notificationId" to notificationId.toString(), "deliusExternalReference" to deliusExternalReference)),
        any(),
      )
    }

    @Test
    fun `Notify returns a non-404 error - rethrows for the listener to handle`() {
      whenever(notificationMappingRepository.findByNotificationId(notificationId)).thenReturn(mapping())
      val serverError = notifyException(500)
      whenever(notificationClient.getNotificationById(notificationId.toString())).thenThrow(serverError)

      assertThrows<NotificationClientException> { handler.handle(event()) }
    }
  }
}

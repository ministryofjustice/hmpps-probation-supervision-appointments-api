package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration

import com.fasterxml.jackson.core.JsonParseException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integrations.NotificationMapping
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.CONTACT_EVENT_ON_CONFIRMED_DELIVERY_FLAG
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.DomainEventService
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.FeatureFlagsService
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service.NotifyDeliveryStatusService
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Verifies that recordStatus rolls back database changes when event publishing
 * throws a checked exception, which Spring does not roll back by default.
 */
class NotifyDeliveryStatusServiceIntegrationTest : IntegrationTestBase() {

  @Autowired
  private lateinit var notifyDeliveryStatusService: NotifyDeliveryStatusService

  @MockitoBean
  private lateinit var domainEventService: DomainEventService

  @MockitoBean
  private lateinit var featureFlagsService: FeatureFlagsService

  @Test
  fun `publish throws a checked exception - status update and claim still roll back`() {
    val notificationId = UUID.randomUUID()
    val crn = "X123456"
    notificationMappingRepository.save(
      NotificationMapping(
        deliusExternalReference = "delius-ref-1",
        notificationId = notificationId,
        templateId = UUID.randomUUID(),
        message = "body",
        crn = crn,
        status = null,
      ),
    )
    whenever(featureFlagsService.isEnabled(CONTACT_EVENT_ON_CONFIRMED_DELIVERY_FLAG)).thenReturn(true)
    // Simulate a checked exception during event publishing to verify transaction rollback.
    // Use thenAnswer because Mockito rejects checked exceptions not declared by Kotlin methods.
    whenever(domainEventService.buildAndPublishContactEvent(crn, notificationId))
      .thenAnswer { throw JsonParseException(null, "boom") }

    assertThrows<JsonParseException> {
      notifyDeliveryStatusService.recordStatus(notificationId, crn, "delivered", createdAt = java.time.Instant.now())
    }

    val mapping = notificationMappingRepository.findByNotificationId(notificationId)
    assertEquals(null, mapping?.status)
    assertEquals(null, mapping?.contactEventPublishedAt)
  }
}

package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.integration.config

import org.mockito.kotlin.mock
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import uk.gov.service.notify.NotificationClient

@TestConfiguration
class NotifyClientTestConfig {

  @Bean
  fun notificationClient(): NotificationClient = mock()
}

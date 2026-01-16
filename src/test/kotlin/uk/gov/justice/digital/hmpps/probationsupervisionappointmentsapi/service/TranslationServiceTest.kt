package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TranslationServiceTest {

  private val service: TranslationService = TranslationService()

  @Test
  fun `should translate day names case-insensitively`() {
    assertEquals("Dydd Llun", service.toWelsh("Monday"))
    assertEquals("Dydd Llun", service.toWelsh("monday"))
    assertEquals("Dydd Llun", service.toWelsh("MONDAY"))
  }
}

package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.util.EnglishToWelshTranslator

class TranslationServiceTest {

  @Test
  fun `should translate day names case-insensitively`() {
    assertEquals("Dydd Llun", EnglishToWelshTranslator.toWelsh("Monday"))
    assertEquals("Dydd Llun", EnglishToWelshTranslator.toWelsh("monday"))
    assertEquals("Dydd Llun", EnglishToWelshTranslator.toWelsh("MONDAY"))
  }
}

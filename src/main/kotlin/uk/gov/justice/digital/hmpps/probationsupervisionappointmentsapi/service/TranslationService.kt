package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import org.springframework.stereotype.Service
import java.util.Locale

@Service
class TranslationService {

  val englishToWelshTranslations: Map<String, String> = mapOf(
    // Days
    "monday" to "Dydd Llun",
    "tuesday" to "Dydd Mawrth",
    "wednesday" to "Dydd Mercher",
    "thursday" to "Dydd Iau",
    "friday" to "Dydd Gwener",
    "saturday" to "Dydd Sadwrn",
    "sunday" to "Dydd Sul",

    // Months
    "january" to "Ionawr",
    "february" to "Chwefror",
    "march" to "Mawrth",
    "april" to "Ebrill",
    "may" to "Mai",
    "june" to "Mehefin",
    "july" to "Gorffennaf",
    "august" to "Awst",
    "september" to "Medi",
    "october" to "Hydref",
    "november" to "Tachwedd",
    "december" to "Rhagfyr",
  )

  fun lookup(english: String): String = englishToWelshTranslations[english.lowercase(Locale.UK)] ?: english

  fun toWelsh(english: String): String = lookup(english)
}

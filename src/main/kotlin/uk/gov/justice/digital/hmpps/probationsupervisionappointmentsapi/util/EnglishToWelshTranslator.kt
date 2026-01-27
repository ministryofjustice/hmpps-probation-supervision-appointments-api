package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.util

import java.util.Locale

object EnglishToWelshTranslator {

  private val translations = mapOf(
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

  fun toWelsh(english: String): String = translations[english.lowercase(Locale.UK)] ?: english
}

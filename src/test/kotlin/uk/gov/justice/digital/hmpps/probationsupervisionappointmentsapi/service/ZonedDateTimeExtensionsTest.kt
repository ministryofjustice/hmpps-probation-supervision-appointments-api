package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ZonedDateTimeExtensionsTest {

  @Test
  fun `toNotifyTime should format hour only when minutes are zero`() {
    val input = ZonedDateTime.of(
      2024,
      9,
      16,
      9,
      0,
      0,
      0,
      ZoneId.of("UTC"),
    )

    val result = input.toNotifyTime()

    // 9 UTC -> 10 BST
    assertEquals("10am", result)
  }

  @Test
  fun `toNotifyTime should include minutes when not zero`() {
    val input = ZonedDateTime.of(
      2024,
      9,
      16,
      9,
      30,
      0,
      0,
      ZoneId.of("UTC"),
    )

    val result = input.toNotifyTime()

    // 9:30 UTC -> 10:30 BST
    assertEquals("10:30am", result)
  }

  @Test
  fun `toNotifyTime should convert to UK time during GMT (winter)`() {
    val input = ZonedDateTime.of(
      2024,
      12,
      16,
      10,
      0,
      0,
      0,
      ZoneId.of("UTC"),
    )

    val result = input.toNotifyTime()

    // Winter → GMT (no offset)
    assertEquals("10am", result)
  }

  @Test
  fun `toNotifyTime should convert to UK time during BST (summer)`() {
    val input = ZonedDateTime.of(
      2024,
      7,
      16,
      10,
      0,
      0,
      0,
      ZoneId.of("UTC"),
    )

    val result = input.toNotifyTime()

    // Summer → BST (+1)
    assertEquals("11am", result)
  }

  @Test
  fun `toNotifyTime should return lowercase am pm`() {
    val input = ZonedDateTime.of(
      2024,
      9,
      16,
      21,
      0,
      0,
      0,
      ZoneId.of("UTC"),
    )

    val result = input.toNotifyTime()

    assertEquals("10pm", result) // 21 UTC → 22 BST
  }

  @Test
  fun `toNotifyDate should format UK date correctly`() {
    val input = ZonedDateTime.of(
      2024,
      9,
      16,
      9,
      0,
      0,
      0,
      ZoneId.of("UTC"),
    )

    val result = input.toNotifyDate()

    assertEquals("Monday 16 September", result)
  }

  @Test
  fun `toNotifyDate should convert timezone before formatting`() {
    val input = ZonedDateTime.of(
      2024,
      9,
      15,
      23,
      30,
      0,
      0,
      ZoneId.of("UTC"),
    )

    val result = input.toNotifyDate()

    // 23:30 UTC -> 00:30 BST next day
    assertEquals("Monday 16 September", result)
  }
}

package uk.gov.justice.digital.hmpps.probationsupervisionappointmentsapi.service

import io.flipt.client.FliptClient
import io.flipt.client.models.BooleanEvaluationResponse
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FeatureFlagsServiceTest {

  private lateinit var client: FliptClient
  private lateinit var featureFlagsService: FeatureFlagsService

  @BeforeEach
  fun setup() {
    client = mock()
    featureFlagsService = FeatureFlagsService(client)
  }

  @Test
  fun `should call evaluateBoolean with lowercase email`() {
    val key = "test-flag"
    val email = "Test.User@Example.COM"
    val expectedLowercase = email.lowercase()

    val response = mock<BooleanEvaluationResponse> {
      on { isEnabled }.thenReturn(true)
    }

    whenever(
      client.evaluateBoolean(
        eq(key),
        eq(email),
        any(),
      ),
    ).thenReturn(response)

    val result = featureFlagsService.isEnabledForUser(key, email)

    assertTrue(result)

    verify(client).evaluateBoolean(
      eq(key),
      eq(email),
      eq(mapOf("recipientEmail" to expectedLowercase)),
    )
  }

  @Test
  fun `should return false when client throws exception`() {
    val key = "test-flag"
    val email = "Test.User@Example.COM"

    whenever(
      client.evaluateBoolean(any(), any(), any()),
    ).thenThrow(RuntimeException("failure"))

    val result = featureFlagsService.isEnabledForUser(key, email)

    assertFalse(result)

    verify(client).evaluateBoolean(
      eq(key),
      eq(email),
      eq(mapOf("recipientEmail" to email.lowercase())),
    )
  }
}

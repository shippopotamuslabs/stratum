package com.stratum.domain.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TelemetryThrowableSanitizerTest {
    @Test
    fun `sanitize returns original throwable when no PII is present`() {
        val original = IllegalStateException("Clean failure")

        val sanitized = TelemetryThrowableSanitizer.sanitize(original)

        assertSame(original, sanitized)
    }

    @Test
    fun `sanitize preserves RuntimeException class and scrubs message without attaching original`() {
        val original = RuntimeException(
            "Failed processing user@example.com at /storage/emulated/0/file.json",
        )
        original.stackTrace = arrayOf(StackTraceElement("Foo", "bar", "Foo.kt", 12))

        val sanitized = TelemetryThrowableSanitizer.sanitize(original)

        assertNotSame(original, sanitized)
        assertEquals(RuntimeException::class.java, sanitized.javaClass)
        assertNull(sanitized.cause)
        assertTrue(sanitized.stackTrace.contentEquals(original.stackTrace))
        assertTrue(sanitized.message!!.contains(TelemetryPiiScrubber.REDACTED_EMAIL))
        assertTrue(sanitized.message!!.contains(TelemetryPiiScrubber.REDACTED_PATH))
        assertFalse(sanitized.message!!.contains("user@example.com"))
    }

    @Test
    fun `sanitize copies cause chain as scrubbed instances of the original classes`() {
        val cause = IllegalStateException("token=secret123")
        val original = RuntimeException("wrapper", cause)

        val sanitized = TelemetryThrowableSanitizer.sanitize(original)

        assertEquals(RuntimeException::class.java, sanitized.javaClass)
        assertEquals("wrapper", sanitized.message)
        val sanitizedCause = sanitized.cause
        assertEquals(IllegalStateException::class.java, sanitizedCause!!.javaClass)
        assertNotSame(cause, sanitizedCause)
        assertTrue(sanitizedCause.message!!.contains(TelemetryPiiScrubber.REDACTED_TOKEN))
        assertFalse(sanitizedCause.message!!.contains("secret123"))
    }

    @Test
    fun `sanitize falls back when the exception type cannot be reconstructed`() {
        val original = IntCodeException(42)

        val sanitized = TelemetryThrowableSanitizer.sanitize(original)

        assertTrue(sanitized is SanitizedTelemetryException)
        assertTrue(sanitized.message!!.contains(IntCodeException::class.java.name))
        assertTrue(sanitized.message!!.contains(TelemetryPiiScrubber.REDACTED_EMAIL))
        assertFalse(sanitized.message!!.contains("user@example.com"))
    }

    private class IntCodeException(val code: Int) : RuntimeException("failed for user@example.com ($code)")
}

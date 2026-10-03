package com.stratum.domain.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
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

    @Test
    fun `sanitize copies suppressed exceptions`() {
        val original = RuntimeException("wrapper")
        original.addSuppressed(IllegalStateException("user@example.com"))

        val sanitized = TelemetryThrowableSanitizer.sanitize(original)

        assertEquals(1, sanitized.suppressed.size)
        assertTrue(sanitized.suppressed.single().message!!.contains(TelemetryPiiScrubber.REDACTED_EMAIL))
        assertFalse(sanitized.suppressed.single().message!!.contains("user@example.com"))
    }

    @Test
    fun `sanitize falls back when string constructor installs a cause`() {
        val original = PrefedCause("failed for user@example.com")

        val sanitized = TelemetryThrowableSanitizer.sanitize(original)

        assertTrue(sanitized is SanitizedTelemetryException)
        assertTrue(sanitized.message!!.contains(TelemetryPiiScrubber.REDACTED_EMAIL))
        val cause = sanitized.cause
        assertTrue(cause!!.message!!.contains(TelemetryPiiScrubber.REDACTED_TOKEN))
        assertFalse(cause.message!!.contains("secret123"))
    }

    @Test
    fun `sanitize attaches cause as suppressed when initCause is locked`() {
        val original = LockedNullCause(
            "failed for user@example.com",
            IllegalStateException("token=secret123"),
        )

        val sanitized = TelemetryThrowableSanitizer.sanitize(original)

        assertEquals(LockedNullCause::class.java, sanitized.javaClass)
        assertNull(sanitized.cause)
        assertEquals(1, sanitized.suppressed.size)
        assertTrue(sanitized.suppressed.single().message!!.contains(TelemetryPiiScrubber.REDACTED_TOKEN))
        assertFalse(sanitized.suppressed.single().message!!.contains("secret123"))
        assertTrue(sanitized.message!!.contains(TelemetryPiiScrubber.REDACTED_EMAIL))
    }

    @Test
    fun `fallback exposes original class name for display and filtering`() {
        val original = IntCodeException(42)

        val result = TelemetryThrowableSanitizer.sanitizeWithNames(original)

        val fallback = assertInstanceOf(SanitizedTelemetryException::class.java, result.sanitized)
        assertEquals(IntCodeException::class.java.name, fallback.originalClassName)
        assertEquals(
            listOf(IntCodeException::class.java.name),
            result.originalClassNames,
        )
    }

    @Test
    fun `sanitize delegates to sanitizeWithNames without changing the result`() {
        val original = IntCodeException(42)

        val sanitized = TelemetryThrowableSanitizer.sanitize(original)
        val result = TelemetryThrowableSanitizer.sanitizeWithNames(IntCodeException(42))

        assertInstanceOf(SanitizedTelemetryException::class.java, sanitized)
        assertEquals(result.originalClassNames, listOf(IntCodeException::class.java.name))
    }

    @Test
    fun `sanitizeWithNames is empty when every node keeps its class`() {
        val cause = IllegalStateException("token=secret123")
        val original = RuntimeException("wrapper", cause)

        val result = TelemetryThrowableSanitizer.sanitizeWithNames(original)

        assertEquals(RuntimeException::class.java, result.sanitized.javaClass)
        assertEquals(
            emptyList<String>(),
            result.originalClassNames,
        )
    }

    @Test
    fun `sanitizeWithNames collects fallbacks across cause and suppressed without duplicates`() {
        val original = RuntimeException("wrapper", IntCodeException(1))
        original.addSuppressed(IntCodeException(2))
        original.addSuppressed(PairCodeException(7, "user@example.com"))

        val result = TelemetryThrowableSanitizer.sanitizeWithNames(original)

        assertEquals(
            listOf(IntCodeException::class.java.name, PairCodeException::class.java.name),
            result.originalClassNames,
        )
    }

    @Test
    fun `sanitizeWithNames terminates on cause cycles`() {
        val first = RuntimeException("failed for user@example.com")
        val second = RuntimeException("clean wrapper", first)
        first.initCause(second)

        val result = TelemetryThrowableSanitizer.sanitizeWithNames(first)

        assertEquals(RuntimeException::class.java, result.sanitized.javaClass)
        assertEquals(
            emptyList<String>(),
            result.originalClassNames,
        )
    }

    @Test
    fun `sanitizeWithNames returns original instance with empty names when no PII is present`() {
        val original = IllegalStateException("Clean failure")

        val result = TelemetryThrowableSanitizer.sanitizeWithNames(original)

        assertSame(original, result.sanitized)
        assertEquals(emptyList<String>(), result.originalClassNames)
    }

    private class IntCodeException(val code: Int) : RuntimeException("failed for user@example.com ($code)")

    private class PairCodeException(val code: Int, val reason: String) :
        RuntimeException("failed $code for $reason")

    private class PrefedCause(message: String) : RuntimeException(message, RuntimeException("token=secret123"))

    private class LockedNullCause(message: String, cause: Throwable?) : RuntimeException(message, cause)
}

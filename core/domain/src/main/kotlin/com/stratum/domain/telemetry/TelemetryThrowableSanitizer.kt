package com.stratum.domain.telemetry

import java.util.IdentityHashMap

/**
 * Copies throwables for Crashlytics so PII is scrubbed from messages without changing the
 * reported exception class or attaching the original (unscrubbed) cause chain.
 */
object TelemetryThrowableSanitizer {
    fun sanitize(throwable: Throwable): Throwable {
        if (!containsPii(throwable, IdentityHashMap())) {
            return throwable
        }
        return copySanitized(throwable, IdentityHashMap())
    }

    private fun containsPii(
        throwable: Throwable,
        visited: IdentityHashMap<Throwable, Boolean>,
    ): Boolean {
        if (visited.put(throwable, true) != null) {
            return false
        }
        val raw = throwable.message
        if (raw != null && TelemetryPiiScrubber.scrub(raw) != raw) {
            return true
        }
        throwable.cause?.let { cause ->
            if (containsPii(cause, visited)) {
                return true
            }
        }
        return throwable.suppressed.any { suppressed -> containsPii(suppressed, visited) }
    }

    private fun copySanitized(
        original: Throwable,
        copies: IdentityHashMap<Throwable, Throwable>,
    ): Throwable {
        copies[original]?.let { return it }
        val scrubbedMessage = TelemetryPiiScrubber.scrub(original.message) ?: original.message.orEmpty()
        val copy = reconstruct(original, scrubbedMessage)
        copies[original] = copy
        copy.stackTrace = original.stackTrace
        val originalCause = original.cause
        if (originalCause != null && originalCause !== original) {
            runCatching { copy.initCause(copySanitized(originalCause, copies)) }
        }
        original.suppressed.forEach { suppressed ->
            if (suppressed !== original) {
                copy.addSuppressed(copySanitized(suppressed, copies))
            }
        }
        return copy
    }

    private fun reconstruct(original: Throwable, message: String): Throwable {
        val type = original.javaClass
        runCatching { type.getConstructor(String::class.java).newInstance(message) }
            .getOrNull()
            ?.let { return it }
        runCatching {
            type.getConstructor(String::class.java, Throwable::class.java).newInstance(message, null)
        }.getOrNull()?.let { return it }
        return SanitizedTelemetryException(type.name, message)
    }
}

internal class SanitizedTelemetryException(
    originalClassName: String,
    message: String,
) : RuntimeException("$originalClassName: $message")

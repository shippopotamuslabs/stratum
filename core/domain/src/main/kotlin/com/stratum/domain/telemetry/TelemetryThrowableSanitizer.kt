package com.stratum.domain.telemetry

import java.util.IdentityHashMap

/**
 * Copies throwables for Crashlytics so PII is scrubbed from messages without changing the
 * reported exception class or attaching the original (unscrubbed) cause chain.
 *
 * Types without a `(String)` or `(String, Throwable)` constructor cannot be reconstructed, so
 * those nodes fall back to [SanitizedTelemetryException]. The fallback keeps the original class
 * name in [SanitizedTelemetryException.originalClassName] (and in the message prefix); telemetry
 * adapters should forward [SanitizedResult.originalClassNames] as Crashlytics custom keys so the
 * original type stays visible for display and filtering. Stack traces are always preserved, so
 * Crashlytics grouping is unaffected.
 */
object TelemetryThrowableSanitizer {
    fun sanitize(throwable: Throwable): Throwable = sanitizeWithNames(throwable).sanitized

    /**
     * Sanitizes [throwable] and collects the original class names of every
     * [SanitizedTelemetryException] fallback node in the chain (top-level, causes,
     * suppressed), in encounter order without duplicates. Empty when every node kept its
     * original class. Class names never contain PII and are safe for telemetry keys.
     */
    fun sanitizeWithNames(throwable: Throwable): SanitizedResult {
        if (!containsPii(throwable, IdentityHashMap())) {
            return SanitizedResult(throwable, emptyList())
        }
        val names = LinkedHashSet<String>()
        val sanitized = copySanitized(throwable, IdentityHashMap(), names)
        return SanitizedResult(sanitized, names.toList())
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
        names: LinkedHashSet<String>,
    ): Throwable {
        copies[original]?.let { return it }
        val scrubbedMessage = TelemetryPiiScrubber.scrub(original.message) ?: original.message.orEmpty()
        val copy = reconstruct(original, scrubbedMessage)
        if (copy is SanitizedTelemetryException) {
            names += copy.originalClassName
        }
        copies[original] = copy
        copy.stackTrace = original.stackTrace
        val originalCause = original.cause
        if (originalCause != null && originalCause !== original) {
            val sanitizedCause = copySanitized(originalCause, copies, names)
            val initialized = runCatching { copy.initCause(sanitizedCause) }
            if (initialized.isFailure && copy.cause !== sanitizedCause) {
                copy.addSuppressed(sanitizedCause)
            }
        }
        original.suppressed.forEach { suppressed ->
            if (suppressed !== original) {
                copy.addSuppressed(copySanitized(suppressed, copies, names))
            }
        }
        return copy
    }

    private fun reconstruct(original: Throwable, message: String): Throwable {
        val type = original.javaClass
        runCatching { type.getConstructor(String::class.java).newInstance(message) }
            .getOrNull()
            ?.takeIf { reconstructed -> reconstructed.cause == null }
            ?.let { return it }
        runCatching {
            type.getConstructor(String::class.java, Throwable::class.java).newInstance(message, null)
        }.getOrNull()
            ?.takeIf { reconstructed -> reconstructed.cause == null }
            ?.let { return it }
        return SanitizedTelemetryException(type.name, message)
    }
}

/**
 * Sanitized copy of a throwable plus the original class names of every
 * [SanitizedTelemetryException] fallback node, in encounter order without duplicates.
 */
data class SanitizedResult(
    val sanitized: Throwable,
    val originalClassNames: List<String>,
)

/**
 * Fallback carrier for exception types that cannot be reconstructed with a scrubbed message
 * (no `(String)` or `(String, Throwable)` constructor). The Crashlytics dashboard shows this
 * class name, so [originalClassName] preserves the lost type for display and filtering via
 * [TelemetryThrowableSanitizer.sanitizeWithNames].
 */
class SanitizedTelemetryException(
    val originalClassName: String,
    message: String,
) : RuntimeException("$originalClassName: $message")

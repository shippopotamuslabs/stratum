package com.stratum.core.infrastructure.telemetry

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
            if (!initCauseOrFalse(copy, sanitizedCause) && copy.cause !== sanitizedCause) {
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

    /**
     * Rebuilds [original] with the scrubbed [message] by direct construction, trying each
     * constructor shape in turn. Every way a shape can be unusable has an explicit fallback
     * to the next shape instead of failing the whole reconstruction:
     * - no public constructor with the wanted signature (looked up in the constructor list,
     *   so a missing shape is a plain null, never an exception),
     * - the constructor itself fails (its body throws, the type is abstract, it is not
     *   accessible),
     * - the constructed instance already carries a cause (a `(String)` constructor that
     *   installs its own cause would re-attach an unscrubbed chain, so that shape is
     *   rejected); the `(String, Throwable)` shape is instead invoked with an explicit
     *   null cause.
     * When neither shape yields a causeless copy, the final fallback is
     * [SanitizedTelemetryException], which keeps the original class name as its
     * `originalClassName` field and in its message. This function never throws for an
     * input the sanitizer accepts.
     */
    private fun reconstruct(original: Throwable, message: String): Throwable {
        val type = original.javaClass
        construct(type, arrayOf(String::class.java), message)
            ?.takeIf { reconstructed -> reconstructed.cause == null }
            ?.let { return it }
        construct(type, arrayOf(String::class.java, Throwable::class.java), message, null)
            ?.takeIf { reconstructed -> reconstructed.cause == null }
            ?.let { return it }
        return SanitizedTelemetryException(type.name, message)
    }

    private fun construct(
        type: Class<out Throwable>,
        parameterTypes: Array<Class<*>>,
        vararg arguments: Any?,
    ): Throwable? {
        val constructor = type.constructors.firstOrNull { candidate ->
            candidate.parameterTypes.contentEquals(parameterTypes)
        } ?: return null
        return try {
            constructor.newInstance(*arguments) as Throwable
        } catch (_: Exception) {
            null
        }
    }

    /**
     * `Throwable.initCause` throws when the cause was already initialized at construction
     * time (including a `(String, Throwable)` construction with an explicit null cause);
     * that state cannot be distinguished from "not yet initialized" through the public
     * API (`cause` reads null in both), so the throw is caught and reported as false,
     * and the caller falls back to attaching the sanitized cause as suppressed.
     */
    private fun initCauseOrFalse(copy: Throwable, sanitizedCause: Throwable): Boolean =
        try {
            copy.initCause(sanitizedCause)
            true
        } catch (_: IllegalStateException) {
            false
        } catch (_: IllegalArgumentException) {
            false
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

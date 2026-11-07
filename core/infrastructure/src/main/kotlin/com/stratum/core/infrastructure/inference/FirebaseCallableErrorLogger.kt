package com.stratum.core.infrastructure.inference

import com.google.firebase.functions.FirebaseFunctionsException

/**
 * Shared Firebase Callable failure-message rendering for inference adapters.
 *
 * The former `logFailure` recorder was removed in STR-240 chunk 8: its last
 * call sites (the A12/A13/A14 interim designated recorders from STR-238) now
 * carry their failures as §10 diagnostics to their boundaries, which record
 * them exactly once. This object stays because adapters whose outcome carries
 * the failure as a diagnostic (e.g. account deletion, STR-238) still render
 * the diagnostic's message with [failureMessage] so the boundary record keeps
 * the exact shape the adapter's former log line used.
 */
internal object FirebaseCallableErrorLogger {
    /**
     * The rendered failure message for a callable failure, without recording
     * it. Adapters whose outcome carries the failure as a §10 diagnostic
     * render the diagnostic's message with this so the boundary
     * record keeps the exact shape a direct log would have produced.
     */
    fun failureMessage(
        callableName: String,
        error: Throwable,
    ): String =
        when (error) {
            is FirebaseFunctionsException ->
                "$callableName callable failed: code=${error.code} message=${error.message}"
            else -> "$callableName failed: ${error.message}"
        }
}

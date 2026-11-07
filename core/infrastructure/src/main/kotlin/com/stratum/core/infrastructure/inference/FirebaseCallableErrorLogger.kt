package com.stratum.core.infrastructure.inference

import com.google.firebase.functions.FirebaseFunctionsException
import com.stratum.core.infrastructure.logging.LoggingPort

/**
 * Shared Firebase Callable failure logging for inference adapters. Keeps tag/message shape consistent
 * across `inferCustomLifts`, `startProgramSynthesis`, etc.
 */
internal object FirebaseCallableErrorLogger {
    fun logFailure(
        loggingPort: LoggingPort,
        tag: String,
        callableName: String,
        error: Throwable,
    ) {
        loggingPort.error(tag, failureMessage(callableName, error), error)
    }

    /**
     * The rendered failure message for a callable failure, without recording
     * it. Adapters whose outcome carries the failure as a §10 diagnostic
     * (STR-238) render the diagnostic's message with this so the boundary
     * record keeps the exact shape [logFailure] would have produced.
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

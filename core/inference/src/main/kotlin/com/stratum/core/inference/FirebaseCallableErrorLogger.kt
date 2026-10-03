package com.stratum.core.inference

import com.google.firebase.functions.FirebaseFunctionsException
import com.stratum.domain.logging.LoggingPort

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
        val message =
            when (error) {
                is FirebaseFunctionsException ->
                    "$callableName callable failed: code=${error.code} message=${error.message}"
                else -> "$callableName failed: ${error.message}"
            }
        loggingPort.error(tag, message, error)
    }
}

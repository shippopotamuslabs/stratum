package com.stratum.core.inference

import com.google.firebase.functions.FirebaseFunctionsException

/**
 * Decides whether a failed `generateProgram` callable should be retried once. A retry is a cheap
 * cache fetch when the server finished after the client hit [FirebaseFunctionsException.Code.DEADLINE_EXCEEDED].
 */
internal object ProgramGenerationCallableRecovery {
    fun isRetriableAfterClientTimeout(error: Throwable): Boolean {
        val functionsError = error.unwrapFirebaseFunctionsException() ?: return false
        return when (functionsError.code) {
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
            FirebaseFunctionsException.Code.UNAVAILABLE,
            -> true
            else -> false
        }
    }

    private fun Throwable.unwrapFirebaseFunctionsException(): FirebaseFunctionsException? {
        var current: Throwable? = this
        while (current != null) {
            if (current is FirebaseFunctionsException) {
                return current
            }
            current = current.cause
        }
        return null
    }
}

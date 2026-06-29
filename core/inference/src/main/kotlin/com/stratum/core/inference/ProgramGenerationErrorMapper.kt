package com.stratum.core.inference

import com.google.firebase.FirebaseException
import com.google.firebase.functions.FirebaseFunctionsException

internal object ProgramGenerationErrorMapper {
    fun userMessage(error: Throwable): String =
        when (error) {
            is ProgramGenerationException ->
                error.message?.takeIf { !looksLikeMetricValidationLeak(it) } ?: GENERIC_FAILURE
            is FirebaseFunctionsException -> mapFunctionsError(error)
            else -> mapGenericError(error)
        }

    private fun mapFunctionsError(error: FirebaseFunctionsException): String =
        when (error.code) {
            FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                "Sign in required to generate your program."
            FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                appCheckOrMessage(error.message)
            FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
                appCheckOrMessage(error.message)
            FirebaseFunctionsException.Code.ALREADY_EXISTS ->
                "Program generation already finished. Tap Generate again to load your program."
            FirebaseFunctionsException.Code.UNAVAILABLE,
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
            ->
                "Generation took longer than expected. Tap Generate again — your program may already be ready."
            FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
                if (looksLikeMetricValidationLeak(error.message)) {
                    GENERIC_FAILURE
                } else {
                    error.message?.takeIf { it.isNotBlank() } ?: GENERIC_FAILURE
                }
            FirebaseFunctionsException.Code.INTERNAL ->
                error.message?.takeIf { it.isNotBlank() } ?: GENERIC_FAILURE
            else -> error.message?.takeIf { it.isNotBlank() } ?: GENERIC_FAILURE
        }

    private fun mapGenericError(error: Throwable): String {
        if (mentionsAppCheck(error)) return APP_CHECK_FAILURE
        return error.message?.takeIf { it.isNotBlank() } ?: GENERIC_FAILURE
    }

    private fun appCheckOrMessage(message: String?): String =
        if (mentionsAppCheckMessage(message)) APP_CHECK_FAILURE else message ?: GENERIC_FAILURE

    private fun mentionsAppCheck(error: Throwable): Boolean =
        mentionsAppCheckMessage(error.message) ||
            (error.cause?.let { mentionsAppCheck(it) } == true)

    private fun mentionsAppCheckMessage(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        return APP_CHECK_MARKERS.any { marker -> message.contains(marker, ignoreCase = true) }
    }

    private fun looksLikeMetricValidationLeak(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        return METRIC_VALIDATION_MARKERS.any { marker -> message.contains(marker, ignoreCase = true) }
    }

    private const val GENERIC_FAILURE = "Program generation failed. Try again."
    private const val APP_CHECK_FAILURE =
        "Couldn't verify this device (App Check). On debug builds, register the App Check debug " +
            "secret from logcat in Firebase Console → App Check → Manage debug tokens."

    private val APP_CHECK_MARKERS =
        listOf(
            "App Check",
            "app check",
            "App attestation failed",
            "attestation failed",
        )

    private val METRIC_VALIDATION_MARKERS =
        listOf(
            "Program metric validation failed",
            "WEEKLY_VOLUME_BELOW_TARGET",
            "MUSCLE_FREQUENCY_LOW",
            "WORKOUT_VOLUME_IMBALANCED",
        )
}

package com.stratum.core.inference

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.stratum.domain.telemetry.CrashTelemetryPort
import com.stratum.domain.telemetry.TelemetryPiiScrubber
import com.stratum.domain.telemetry.TelemetryThrowableSanitizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [CrashTelemetryPort] backed by Firebase Crashlytics for user attribution, custom keys, screen tracking,
 * and session breadcrumbs. Values are scrubbed for PII before transmission.
 */
@Singleton
class AndroidCrashTelemetryAdapter @Inject constructor() : CrashTelemetryPort {
    override fun setUserId(userId: String?) {
        runCatching {
            FirebaseCrashlytics.getInstance().setUserId(userId.orEmpty())
        }.onFailure { error ->
            Log.e(TAG, "Crashlytics setUserId failed", error)
        }
    }

    override fun setScreen(screenName: String) {
        setScrubbedKey(KEY_CURRENT_SCREEN, screenName, "setScreen")
        recordBreadcrumb("Screen: $screenName")
    }

    override fun setActiveWorkoutId(workoutId: String?) {
        setScrubbedKey(KEY_ACTIVE_WORKOUT_ID, workoutId.orEmpty(), "setActiveWorkoutId")
    }

    override fun setSubscriptionTier(tier: String) {
        setScrubbedKey(KEY_SUBSCRIPTION_TIER, tier, "setSubscriptionTier")
    }

    override fun recordBreadcrumb(message: String) {
        val scrubbedMessage = TelemetryPiiScrubber.scrub(message).orEmpty()
        runCatching {
            FirebaseCrashlytics.getInstance().log(scrubbedMessage)
        }.onFailure { error ->
            Log.e(TAG, "Crashlytics recordBreadcrumb failed: $scrubbedMessage", error)
        }
    }

    override fun setFlagState(
        key: String,
        enabled: Boolean,
    ) {
        setScrubbedKey(KEY_FLAG_PREFIX + key, enabled.toString(), "setFlagState")
    }

    override fun recordNonFatal(throwable: Throwable) {
        val result = TelemetryThrowableSanitizer.sanitizeWithNames(throwable)
        setSanitizedOriginalClassKeys(result.originalClassNames)
        runCatching {
            FirebaseCrashlytics.getInstance().recordException(result.sanitized)
        }.onFailure { error ->
            Log.e(TAG, "Crashlytics recordNonFatal failed: ${result.sanitized.message}", error)
        }
    }

    /**
     * Preserves exception types lost to [TelemetryThrowableSanitizer] fallback copies as custom
     * keys so the Crashlytics dashboard can display and filter by the original class name.
     * Keys do not affect issue grouping (grouping uses the preserved stack trace). All slots are
     * always written so a previous non-fatal's class never leaks onto an unrelated report.
     */
    private fun setSanitizedOriginalClassKeys(originalClassNames: List<String>) {
        for ((key, value) in toKeyMap(originalClassNames)) {
            setScrubbedKey(key, value, "recordNonFatal")
        }
    }

    private fun keyForIndex(index: Int): String =
        if (index == 0) {
            KEY_SANITIZED_ORIGINAL_CLASS
        } else {
            "${KEY_SANITIZED_ORIGINAL_CLASS}_${index + 1}"
        }

    private fun toKeyMap(originalClassNames: List<String>): Map<String, String> =
        (0 until MAX_ORIGINAL_CLASS_KEYS).associate { index ->
            keyForIndex(index) to originalClassNames.getOrElse(index) { "" }
        }

    private fun setScrubbedKey(key: String, value: String, operation: String) {
        val scrubbed = TelemetryPiiScrubber.scrub(value).orEmpty()
        runCatching {
            FirebaseCrashlytics.getInstance().setCustomKey(key, scrubbed)
        }.onFailure { error ->
            Log.e(TAG, "Crashlytics $operation failed for $key=$scrubbed", error)
        }
    }

    private companion object {
        const val TAG = "CrashTelemetry"
        const val KEY_FLAG_PREFIX = "flag_"
        const val KEY_CURRENT_SCREEN = "current_screen"
        const val KEY_ACTIVE_WORKOUT_ID = "active_workout_id"
        const val KEY_SUBSCRIPTION_TIER = "subscription_tier"
        const val KEY_SANITIZED_ORIGINAL_CLASS = "sanitized_original_class"

        /**
         * Number of Crashlytics custom keys reserved for fallback original class names. Only
         * the first [MAX_ORIGINAL_CLASS_KEYS] distinct names are forwarded (extras are dropped);
         * missing slots are written as empty strings so stale values never leak across reports.
         */
        const val MAX_ORIGINAL_CLASS_KEYS = 3
    }
}

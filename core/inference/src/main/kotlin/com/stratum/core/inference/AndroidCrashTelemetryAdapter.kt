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
        val targetThrowable = TelemetryThrowableSanitizer.sanitize(throwable)
        runCatching {
            FirebaseCrashlytics.getInstance().recordException(targetThrowable)
        }.onFailure { error ->
            Log.e(TAG, "Crashlytics recordNonFatal failed: ${targetThrowable.message}", error)
        }
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
    }
}

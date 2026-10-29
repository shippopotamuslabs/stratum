package com.stratum.domain.telemetry

import com.stratum.domain.logging.LoggingPort

/**
 * Domain port for Crashlytics/telemetry session attribution, screen tracking, and user context.
 * Separated from [com.stratum.domain.logging.LoggingPort] to keep log levels decoupled from
 * telemetry session state.
 */
interface CrashTelemetryPort {
    fun setUserId(userId: String?)

    fun setScreen(screenName: String)

    fun setActiveWorkoutId(workoutId: String?)

    fun setSubscriptionTier(tier: String)

    fun recordBreadcrumb(message: String)

    fun recordNonFatal(throwable: Throwable)

    /**
     * Records a feature-flag evaluation so crash reports can be filtered by flag state.
     * Platform adapters namespace the key (e.g. `flag_<key>`).
     */
    fun setFlagState(
        key: String,
        enabled: Boolean,
    )
}


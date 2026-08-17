package com.stratum.domain.telemetry

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
}


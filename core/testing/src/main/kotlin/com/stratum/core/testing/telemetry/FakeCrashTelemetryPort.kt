package com.stratum.core.testing.telemetry

import com.stratum.domain.telemetry.CrashTelemetryPort

/**
 * In-memory [CrashTelemetryPort] for unit tests. Records user ID, screen name, breadcrumbs,
 * non-fatals, and flag states. Does not clone production mapping (custom keys / screen
 * breadcrumbs).
 */
class FakeCrashTelemetryPort : CrashTelemetryPort {
    var currentUserId: String? = null
        private set
    var currentScreen: String? = null
        private set
    var activeWorkoutId: String? = null
        private set
    var subscriptionTier: String? = null
        private set
    val flagStates = mutableMapOf<String, Boolean>()
    val breadcrumbs = mutableListOf<String>()
    val recordedExceptions = mutableListOf<Throwable>()

    override fun setUserId(userId: String?) {
        currentUserId = userId
    }

    override fun setScreen(screenName: String) {
        currentScreen = screenName
    }

    override fun setActiveWorkoutId(workoutId: String?) {
        activeWorkoutId = workoutId
    }

    override fun setSubscriptionTier(tier: String) {
        subscriptionTier = tier
    }

    override fun setFlagState(
        key: String,
        enabled: Boolean,
    ) {
        flagStates[key] = enabled
    }

    override fun recordBreadcrumb(message: String) {
        breadcrumbs += message
    }

    override fun recordNonFatal(throwable: Throwable) {
        recordedExceptions += throwable
    }

    fun clear() {
        currentUserId = null
        currentScreen = null
        activeWorkoutId = null
        subscriptionTier = null
        flagStates.clear()
        breadcrumbs.clear()
        recordedExceptions.clear()
    }
}


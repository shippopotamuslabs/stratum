package com.stratum.core.testing.telemetry

import com.stratum.domain.telemetry.CrashTelemetryPort

/**
 * In-memory [CrashTelemetryPort] for unit tests. Records user ID, screen name, breadcrumbs, and
 * non-fatals. Does not clone production mapping (custom keys / screen breadcrumbs).
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
        breadcrumbs.clear()
        recordedExceptions.clear()
    }
}


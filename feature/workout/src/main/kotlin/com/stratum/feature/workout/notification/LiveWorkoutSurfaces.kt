package com.stratum.feature.workout.notification

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bundles process-scoped surfaces used by the live workout (notifications + rest timer)
 * so ViewModels take a single constructor dependency instead of two.
 */
@Singleton
class LiveWorkoutSurfaces @Inject constructor(
    val activeWorkout: ActiveWorkoutTracker,
    val restTimer: RestTimerController,
    val restCompletion: RestTimerCompletion,
) {
    fun clearSession() {
        activeWorkout.clear()
        restTimer.cancel()
    }
}

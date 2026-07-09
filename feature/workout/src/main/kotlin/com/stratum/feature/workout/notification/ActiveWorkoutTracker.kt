package com.stratum.feature.workout.notification

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveWorkoutSnapshot(
    val workoutId: String,
    val workoutName: String,
    val startedAtEpochMs: Long,
)

/**
 * Process-scoped pointer to the live training session for notification surfaces.
 * [WorkoutViewModel] publishes while a session is loaded and clears on finalize/abandon.
 */
@Singleton
class ActiveWorkoutTracker @Inject constructor() {
    private val _active = MutableStateFlow<ActiveWorkoutSnapshot?>(null)
    val active: StateFlow<ActiveWorkoutSnapshot?> = _active.asStateFlow()

    fun setActive(workoutId: String, workoutName: String, startedAtEpochMs: Long) {
        _active.value = ActiveWorkoutSnapshot(
            workoutId = workoutId,
            workoutName = workoutName,
            startedAtEpochMs = startedAtEpochMs,
        )
    }

    fun clear() {
        _active.value = null
    }
}

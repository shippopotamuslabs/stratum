package com.stratum.feature.workout.notification

/**
 * Process-scoped rest timer state. [Running.endsAtEpochMs] is the source of truth;
 * remaining time is always derived from the wall clock (not a free-running tick counter).
 */
sealed interface RestTimerState {
    data object Idle : RestTimerState

    data class Running(
        val endsAtEpochMs: Long,
        val totalSeconds: Int,
        val workoutId: String,
        val workoutName: String,
    ) : RestTimerState
}

object RestTimerDefaults {
    const val DEFAULT_REST_SECONDS = 90
    const val MAX_REST_SECONDS = 300
    const val ADJUST_STEP_SECONDS = 10
}

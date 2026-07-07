package com.stratum.feature.workout.ui

/**
 * Presentation state for the workout completion screen. Domain models never reach
 * composables (ARCHITECTURE.md §4.3).
 */
sealed interface WorkoutCompleteUiState {
    data object Loading : WorkoutCompleteUiState
    data object Error : WorkoutCompleteUiState

    data class Loaded(
        val workoutName: String,
        val durationFormatted: String,
        val bestSets: List<LiftBestSetUi>,
    ) : WorkoutCompleteUiState
}

data class LiftBestSetUi(
    val liftName: String,
    val bestSetLabel: String,
)

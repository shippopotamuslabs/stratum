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

/**
 * One row in the per-lift performance table. All numeric values arrive as
 * presentation strings — the composables never see raw doubles/ints.
 */
data class LiftBestSetUi(
    val liftName: String,
    val performanceLabel: String,
    val rirLabel: String,
    val estimatedOneRmLabel: String,
    val isPersonalRecord: Boolean,
)

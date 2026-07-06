package com.stratum.feature.workout.ui

/**
 * Presentation state for the live workout screen (Slice 7). Domain models never reach
 * composables (ARCHITECTURE.md §4.3).
 */
sealed interface WorkoutUiState {
    data object Loading : WorkoutUiState
    data object NoActiveSession : WorkoutUiState

    data class Loaded(
        val workoutName: String,
        val lifts: List<RenderedLiftUi>,
        val isFinalizing: Boolean,
    ) : WorkoutUiState

    data class Error(val message: String) : WorkoutUiState
}

data class RenderedLiftUi(
    val workoutLiftId: String,
    val liftName: String,
    val progressionSchemeLabel: String,
    val sets: List<RenderedSetUi>,
    val supersetGroupId: String? = null,
)

data class RenderedSetUi(
    val slotKey: String,
    val positionLabel: String,
    val setTypeLabel: String,
    val repRangeLabel: String?,
    val rirLabel: String,
    val recommendedWeightLabel: String,
    val recommendedRepsLabel: String,
    val loggedWeightLabel: String?,
    val loggedRepsLabel: String?,
    val isComplete: Boolean,
    val previousWeightLabel: String? = null,
    val rirGoal: Int,
    val repMin: Int,
    val repMax: Int,
    val recommendedWeightAmount: Double,
    val recommendedReps: Int,
    val loggedRir: Int? = null,
)

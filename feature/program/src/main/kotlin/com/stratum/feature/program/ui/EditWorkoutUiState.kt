package com.stratum.feature.program.ui

import com.stratum.core.ui.component.StratumMuscleVolumeCardState

/**
 * Presentation state for [EditWorkoutScreen]. Domain models never reach composables
 * (ARCHITECTURE.md §4.3).
 */
sealed interface EditWorkoutUiState {
    data object Loading : EditWorkoutUiState

    data object NotFound : EditWorkoutUiState

    data class Loaded(
        val workoutId: String,
        val workoutName: String,
        val weekdayLabel: String,
        val lifts: List<EditLiftState>,
        val upperVolumeByMuscle: List<StratumMuscleVolumeCardState> = emptyList(),
        val lowerVolumeByMuscle: List<StratumMuscleVolumeCardState> = emptyList(),
        val estimatedDurationMinutes: Int = 0,
        val isReorderMode: Boolean = false,
        val pendingReorderLifts: List<EditLiftReorderState>? = null,
    ) : EditWorkoutUiState
}

data class EditLiftState(
    val workoutLiftId: String,
    val liftId: String,
    val name: String,
    val position: Int,
    val sets: List<EditSetState>,
    val supersetGroupId: Int? = null,
)

data class EditSetState(
    val setId: String,
    val setNumber: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
    val rirGoal: Int,
)

/** Minimal lift row shown during reorder mode. */
data class EditLiftReorderState(
    val workoutLiftId: String,
    val name: String,
)

internal const val DEFAULT_REST_SECONDS = 120
internal const val PLACEHOLDER_LIFT_NAME = "New Exercise"
internal const val DEFAULT_REP_MIN = 8
internal const val DEFAULT_REP_MAX = 12
internal const val DEFAULT_RIR_GOAL = 2
internal const val SIX_PLUS_RIR_GOAL = 6

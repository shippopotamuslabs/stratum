package com.stratum.feature.program.ui

import com.stratum.core.ui.component.StratumMuscleVolumeCardState

/**
 * Read-only presentation of a generated program (OS-7 D3). Domain models are never passed to
 * composables (ARCHITECTURE.md §4.3); these view states are the presentation boundary.
 */
sealed interface ProgramsUiState {
    val status: Status

    data object Empty : ProgramsUiState {
        override val status: Status = Status.Empty
    }

    data class Loaded(
        val program: ProgramViewState,
        val upperVolumeByMuscle: List<StratumMuscleVolumeCardState>,
        val lowerVolumeByMuscle: List<StratumMuscleVolumeCardState>,
        val scheduleItems: List<ScheduleItemState>,
    ) : ProgramsUiState {
        override val status: Status = Status.Loaded
    }

    enum class Status { Empty, Loaded }
}

data class ProgramViewState(
    val name: String,
)

sealed interface ScheduleItemState {
    data class RestDay(val weekdayLabel: String) : ScheduleItemState

    data class Workout(val workout: WorkoutViewState) : ScheduleItemState
}

data class WorkoutViewState(
    val workoutId: String,
    val name: String,
    val weekdayLabel: String,
    val position: Int,
    val lifts: List<WorkoutLiftLineState>,
    val isCompleted: Boolean = false,
)

/** Structured lift summary rendered as a two-row sub card on workout cards. */
data class WorkoutLiftLineState(
    val workoutLiftId: String,
    val liftId: String,
    val name: String,
    val setCount: Int,
    val repRange: String,
    val topSetRpe: String,
    val position: Int,
)

data class MinimizedWorkoutState(
    val workoutId: String,
    val workoutName: String,
    val nextLiftName: String,
)

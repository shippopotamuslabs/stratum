package com.stratum.feature.program.ui

/**
 * Read-only presentation of a generated program (OS-7 D3). Domain models are never passed to
 * composables (ARCHITECTURE.md §4.1); these view states are the presentation boundary.
 *
 * `isExpanded` on [WorkoutLiftViewState] toggles set visibility. No edit/swap/regenerate affordance
 * is present — those are deferred to a follow-up.
 */
sealed interface ProgramsUiState {
    val status: Status

    data object Empty : ProgramsUiState {
        override val status: Status = Status.Empty
    }

    data class Loaded(
        val program: ProgramViewState,
    ) : ProgramsUiState {
        override val status: Status = Status.Loaded
    }

    enum class Status { Empty, Loaded }
}

data class ProgramViewState(
    val name: String,
    val workouts: List<WorkoutViewState>,
)

data class WorkoutViewState(
    val name: String,
    val weekdayLabel: String?,
    val position: Int,
    val workoutLifts: List<WorkoutLiftViewState>,
)

data class WorkoutLiftViewState(
    val liftId: String,
    val liftName: String,
    val progressionSchemeLabel: String,
    val position: Int,
    val isExpanded: Boolean,
    val sets: List<LiftSetViewState>,
)

data class LiftSetViewState(
    val position: Int,
    val setTypeLabel: String,
    val repMin: Int,
    val repMax: Int,
    val rirGoal: Int,
    val isTopSet: Boolean,
)

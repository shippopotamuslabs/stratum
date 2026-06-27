package com.stratum.feature.program.ui

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

/** One line per lift: `Lift Name - 3 x 8–12`. */
data class WorkoutLiftViewState(
    val summaryLine: String,
    val position: Int,
)

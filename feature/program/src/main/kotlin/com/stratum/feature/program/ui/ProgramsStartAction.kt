package com.stratum.feature.program.ui

/**
 * Represents a pending workout-start action that may require discarding an active session
 * first. Used by the start-enforcement dialog on the Programs screen.
 */
sealed interface ProgramsStartAction {
    data class StartWorkout(val workoutId: String) : ProgramsStartAction
    data object StartNextWorkout : ProgramsStartAction
    data object StartEmptyWorkout : ProgramsStartAction
}

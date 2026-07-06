package com.stratum.feature.program.ui

/**
 * One-shot navigation and feedback events from [com.stratum.feature.program.ProgramsViewModel].
 * Collected in [com.stratum.feature.program.ProgramsRoute] — not persisted in UI state.
 */
sealed interface ProgramsUiEvent {
    data class NavigateToWorkout(val workoutId: String) : ProgramsUiEvent

    data class ShowMessage(val message: String) : ProgramsUiEvent
}

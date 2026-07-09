package com.stratum.feature.program.ui

/**
 * One-shot navigation and feedback events from [com.stratum.feature.program.ProgramsViewModel].
 * Collected in [com.stratum.feature.program.ProgramsRoute] — not persisted in UI state.
 */
/**
 * One-shot UI events for the Programs screen. Transient snackbar copy uses
 * [com.stratum.feature.program.ProgramsViewModel.userMessage] (UI state), not this channel.
 */
sealed interface ProgramsUiEvent {
    data class NavigateToWorkout(val workoutId: String) : ProgramsUiEvent
}

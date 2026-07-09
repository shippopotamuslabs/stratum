package com.stratum.feature.workout.ui

/**
 * One-shot UI events for the live workout screen (Slice 7). Consumed via a
 * `Channel<WorkoutUiEvent>(Channel.BUFFERED).receiveAsFlow()` at the screen level.
 *
 * Transient snackbar copy uses [WorkoutViewModel.userMessage] (UI state), not this channel.
 * Note: no `PromptSchemeTransition` event — D8 locks auto-transition with no confirm.
 */
sealed interface WorkoutUiEvent {
    data class NavigateToFinalize(val sessionId: String) : WorkoutUiEvent
}

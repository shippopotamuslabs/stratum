package com.stratum.feature.program.ui

/**
 * One-shot UI events for Edit Workout. Collected in [EditWorkoutRoute] — not persisted in UI state.
 * Transient snackbar copy uses [com.stratum.feature.program.EditWorkoutViewModel.userMessage].
 */
sealed interface EditWorkoutUiEvent {
    data class NavigateToWorkout(val workoutId: String) : EditWorkoutUiEvent

    /** Fresh active-session check failed open: UI must confirm abandon before start. */
    data object ShowActiveSessionDiscardDialog : EditWorkoutUiEvent
}

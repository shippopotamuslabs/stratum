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

    /**
     * Emitted only after a set is successfully persisted for the **first** time and the UI
     * state has been re-rendered. Drive rest timer + autoscroll + complete-sheet from this
     * event so failed logs do not advance the session chrome optimistically.
     */
    data class SetLogged(val slotKey: String) : WorkoutUiEvent

    /**
     * Emitted after a successful amend of an already-logged set (no rest/focus side effects).
     * Lets the draft store clear pending materialise once durable state is updating.
     */
    data class SetAmended(val slotKey: String) : WorkoutUiEvent

    /**
     * Persist failed after the UI optimistically materialised drafts — consumers should
     * reseed that slot from durable labels.
     */
    data class SetLogFailed(val slotKey: String) : WorkoutUiEvent
}

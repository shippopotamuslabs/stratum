package com.stratum.feature.workout.ui

/**
 * One-shot events from [com.stratum.feature.workout.WorkoutCompleteViewModel].
 * Collected in `WorkoutCompleteRoute` (ARCHITECTURE.md §4.2: `Channel` + `receiveAsFlow`).
 */
sealed interface WorkoutCompleteEvent {
    /**
     * The user chose "Edit steps" on an equipment proposal: open the increments editor
     * for the proposal's write target, seeded with the proposed tiers. The card is
     * already removed session-side — cancelling the editor writes nothing and the
     * proposal resurfaces next session only if the weights are still off-lattice.
     */
    data class OpenEquipmentEditor(
        val request: EquipmentEditRequest,
    ) : WorkoutCompleteEvent
}

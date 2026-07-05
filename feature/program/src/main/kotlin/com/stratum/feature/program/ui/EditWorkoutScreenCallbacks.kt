package com.stratum.feature.program.ui

/**
 * Bundles edit-workout mutation callbacks to keep [EditWorkoutScreen] under the detekt parameter threshold.
 */
data class EditWorkoutScreenCallbacks(
    val onToggleReorderMode: () -> Unit = {},
    val onConfirmReorder: () -> Unit = {},
    val onReorderLifts: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    val onAddSet: (workoutLiftId: String) -> Unit = {},
    val onRemoveSet: (setId: String) -> Unit = {},
    val onAddLift: () -> Unit = {},
    val onRemoveLift: (workoutLiftId: String) -> Unit = {},
    val onLinkSupersetWithPrevious: (workoutLiftId: String) -> Unit = {},
    val onLinkSupersetWithNext: (workoutLiftId: String) -> Unit = {},
    val onRemoveFromSuperset: (workoutLiftId: String) -> Unit = {},
    val onSetRestSeconds: (setId: String, restSeconds: Int) -> Unit = { _, _ -> },
    val onSetRestSecondsForAllSetsOnLift: (workoutLiftId: String, restSeconds: Int) -> Unit = { _, _ -> },
    val onSetRepMin: (setId: String, repMin: Int) -> Unit = { _, _ -> },
    val onSetRepMax: (setId: String, repMax: Int) -> Unit = { _, _ -> },
    val onSetRirGoal: (setId: String, rirGoal: Int) -> Unit = { _, _ -> },
    val onSwapLiftClick: (workoutLiftId: String, liftId: String, liftName: String) -> Unit = { _, _, _ -> },
)

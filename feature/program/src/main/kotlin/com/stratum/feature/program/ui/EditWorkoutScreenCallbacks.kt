package com.stratum.feature.program.ui

import com.stratum.domain.program.ProgressionScheme

/**
 * Bundles edit-workout mutation callbacks to keep [EditWorkoutScreen] under the detekt parameter threshold.
 */
data class EditWorkoutScreenCallbacks(
    val onAddSet: (workoutLiftId: String) -> Unit = {},
    val onRemoveSet: (setId: String) -> Unit = {},
    val onAddLift: () -> Unit = {},
    val onRemoveLift: (workoutLiftId: String) -> Unit = {},
    val onLinkSupersetWithPrevious: (workoutLiftId: String) -> Unit = {},
    val onLinkSupersetWithNext: (workoutLiftId: String) -> Unit = {},
    val onRemoveFromSuperset: (workoutLiftId: String) -> Unit = {},
    val onSetRestSeconds: (setId: String, restSeconds: Int) -> Unit = { _, _ -> },
    val onSetRestSecondsForAllSetsOnLift: (workoutLiftId: String, restSeconds: Int) -> Unit = { _, _ -> },
    /** Atomic min+max commit from the rep-range pad (dismiss / leave with valid drafts). */
    val onSetRepBand: (setId: String, repMin: Int, repMax: Int) -> Unit = { _, _, _ -> },
    val onSetRirGoal: (setId: String, rirGoal: Int) -> Unit = { _, _ -> },
    val onSetUseRestTimerBetweenLifts: (workoutLiftId: String, enabled: Boolean) -> Unit = { _, _ -> },
    val onSetProgressionScheme: (workoutLiftId: String, scheme: ProgressionScheme) -> Unit = { _, _ -> },
    val onSaveWorkout: () -> Unit = {},
    val onSwapLiftClick: (workoutId: String, workoutLiftId: String?, liftId: String?, liftName: String?) -> Unit = { _, _, _, _ -> },
    val onReorderLiftsClick: () -> Unit = {},
)

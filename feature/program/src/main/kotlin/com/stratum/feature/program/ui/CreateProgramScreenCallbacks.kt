package com.stratum.feature.program.ui

/**
 * Bundles Create Program screen callbacks to stay under detekt parameter thresholds.
 */
data class CreateProgramScreenCallbacks(
    val onBack: () -> Unit = {},
    val onSelectGenerate: () -> Unit = {},
    val onSelectScratch: () -> Unit = {},
    val onMuscleToggled: (String) -> Unit = {},
    val onMinimumVolumeMuscleToggled: (String) -> Unit = {},
    val onSuggestedMinimumVolumeToggled: (String) -> Unit = {},
    val onTrainingDayCountChanged: (Int) -> Unit = {},
    val onSessionPresetSelected: (String?) -> Unit = {},
    val onCustomSessionMinutesChange: (Int) -> Unit = {},
    val onSixDayCycleLengthSelected: (Int) -> Unit = {},
    val onTrainingSlotToggled: (Int) -> Unit = {},
    val onNext: () -> Unit = {},
    val onGenerate: () -> Unit = {},
    val onSelectDay: (String) -> Unit = {},
    val onAddDay: () -> Unit = {},
    val onRemoveDay: () -> Unit = {},
    val onShowRenameWorkout: () -> Unit = {},
    val onDismissRenameWorkout: () -> Unit = {},
    val onRenameProgram: (String) -> Unit = {},
    val onRenameWorkout: (String) -> Unit = {},
    val onAddExercise: () -> Unit = {},
    /** Prefer typed mutation callbacks; retained for any residual transform-style callers. */
    val onUpdateSelectedWorkout: ((EditWorkoutUiState.Loaded) -> EditWorkoutUiState.Loaded) -> Unit = {},
    val onRemoveLift: (workoutLiftId: String) -> Unit = {},
    val onLinkSupersetWithPrevious: (workoutLiftId: String) -> Unit = {},
    val onLinkSupersetWithNext: (workoutLiftId: String) -> Unit = {},
    val onRemoveFromSuperset: (workoutLiftId: String) -> Unit = {},
    val onSetUseRestTimerBetweenLifts: (workoutLiftId: String, enabled: Boolean) -> Unit = { _, _ -> },
    val onAddSet: (workoutLiftId: String) -> Unit = {},
    val onRemoveSet: (setId: String) -> Unit = {},
    val onSetRepBand: (setId: String, repMin: Int, repMax: Int) -> Unit = { _, _, _ -> },
    val onSetRirGoal: (setId: String, rirGoal: Int) -> Unit = { _, _ -> },
    val onSetRestSeconds: (setId: String, restSeconds: Int) -> Unit = { _, _ -> },
    val onSetRestSecondsForAllSetsOnLift: (workoutLiftId: String, restSeconds: Int) -> Unit =
        { _, _ -> },
    val onReorderLifts: () -> Unit = {},
    /** Apply local day reorder after the full-screen reorder host confirms. */
    val onDaysReordered: (orderedDayIds: List<String>) -> Unit = {},
    val onSwapLift: (workoutId: String, workoutLiftId: String?, liftId: String?, liftName: String?) -> Unit =
        { _, _, _, _ -> },
    val onSaveToLibrary: () -> Unit = {},
    val onSaveChanges: () -> Unit = {},
    val onActivate: () -> Unit = {},
    val onConfirmDiscard: () -> Unit = {},
    val onDismissDiscard: () -> Unit = {},
    val onConfirmActiveSessionDiscard: () -> Unit = {},
    val onDismissActiveSessionDiscard: () -> Unit = {},
)

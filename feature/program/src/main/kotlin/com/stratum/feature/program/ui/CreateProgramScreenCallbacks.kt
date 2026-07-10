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
    val onWeekdayToggled: (String) -> Unit = {},
    val onSessionPresetSelected: (String?) -> Unit = {},
    val onCustomSessionMinutesChange: (Int) -> Unit = {},
    val onNext: () -> Unit = {},
    val onGenerate: () -> Unit = {},
    val onSelectDay: (String) -> Unit = {},
    val onAddDay: () -> Unit = {},
    val onReorderDays: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    val onRemoveDay: () -> Unit = {},
    val onShowRenameProgram: () -> Unit = {},
    val onShowRenameWorkout: () -> Unit = {},
    val onDismissRenameProgram: () -> Unit = {},
    val onDismissRenameWorkout: () -> Unit = {},
    val onRenameProgram: (String) -> Unit = {},
    val onRenameWorkout: (String) -> Unit = {},
    val onAddExercise: () -> Unit = {},
    val onUpdateSelectedWorkout: ((EditWorkoutUiState.Loaded) -> EditWorkoutUiState.Loaded) -> Unit = {},
    val onReorderLifts: () -> Unit = {},
    val onSwapLift: (workoutId: String, workoutLiftId: String?, liftId: String?, liftName: String?) -> Unit =
        { _, _, _, _ -> },
    val onSaveToLibrary: () -> Unit = {},
    val onSaveChanges: () -> Unit = {},
    val onActivate: () -> Unit = {},
    val onConfirmDiscard: () -> Unit = {},
    val onDismissDiscard: () -> Unit = {},
)

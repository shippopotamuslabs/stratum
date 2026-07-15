package com.stratum.feature.program.ui

/**
 * Bundles Programs screen callbacks to keep [ProgramsScreen] under the detekt parameter threshold.
 */
data class ProgramsScreenCallbacks(
    val onCalendarClick: () -> Unit = {},
    val onCreateProgramClick: () -> Unit = {},
    val onEditProgramClick: (programId: String) -> Unit = {},
    val onReorderWorkoutsClick: (programId: String) -> Unit = {},
    val onProgramLibraryClick: () -> Unit = {},
    val onStartNextWorkoutClick: () -> Unit = {},
    val onStartEmptyWorkoutClick: () -> Unit = {},
    val onStartWorkoutClick: (String) -> Unit = {},
    val onEditWorkoutClick: (workoutId: String) -> Unit = {},
    val onSkipWorkoutCheckedChange: (workoutId: String, checked: Boolean) -> Unit = { _, _ -> },
    /** Rest skip/unskip by cycle [slotIndex] (not weekday identity). */
    val onSkipRestDayCheckedChange: (slotIndex: Int, checked: Boolean) -> Unit = { _, _ -> },
    val onKeepProgramClick: () -> Unit = {},
    val onDiscardProgramClick: () -> Unit = {},
    val onConfirmDiscardAndStart: (ProgramsStartAction) -> Unit = {},
    /** Persists which workout card is expanded (`null` when collapsed). */
    val onExpandedWorkoutChange: (workoutId: String?) -> Unit = {},
)

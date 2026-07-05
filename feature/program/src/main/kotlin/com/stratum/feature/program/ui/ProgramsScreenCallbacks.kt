package com.stratum.feature.program.ui

/**
 * Bundles Programs screen callbacks to keep [ProgramsScreen] under the detekt parameter threshold.
 */
data class ProgramsScreenCallbacks(
    val onCalendarClick: () -> Unit = {},
    val onCreateProgramClick: () -> Unit = {},
    val onGenerateProgramClick: () -> Unit = {},
    val onProgramLibraryClick: () -> Unit = {},
    val onStartNextWorkoutClick: () -> Unit = {},
    val onStartEmptyWorkoutClick: () -> Unit = {},
    val onStartWorkoutClick: (String) -> Unit = {},
    val onEditWorkoutClick: (workoutId: String) -> Unit = {},
    val onSkipWorkoutCheckedChange: (String, Boolean) -> Unit = { _, _ -> },
    val onKeepProgramClick: () -> Unit = {},
    val onDiscardProgramClick: () -> Unit = {},
)

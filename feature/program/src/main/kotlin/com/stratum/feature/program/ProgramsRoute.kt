package com.stratum.feature.program

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.feature.program.ui.ProgramsScreen
import com.stratum.feature.program.ui.ProgramsUiState

@Composable
fun ProgramsRoute(
    viewModel: ProgramsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
    onCalendarClick: () -> Unit = {},
    onCreateProgramClick: () -> Unit = {},
    onGenerateProgramClick: () -> Unit = {},
    onProgramLibraryClick: () -> Unit = {},
    onStartNextWorkoutClick: () -> Unit = {},
    onStartEmptyWorkoutClick: () -> Unit = {},
    onStartWorkoutClick: (String) -> Unit = {},
    onSkipWorkoutCheckedChange: (String, Boolean) -> Unit = { _, _ -> },
    onSwapLiftClick: (workoutLiftId: String, liftId: String, liftName: String) -> Unit = { _, _, _ -> },
    isPreviewMode: Boolean = false,
    onKeepProgramClick: () -> Unit = {},
    onDiscardProgramClick: () -> Unit = {},
) {
    val state by viewModel.programsUiState.collectAsStateWithLifecycle()
    ProgramsScreen(
        state = state,
        onCalendarClick = onCalendarClick,
        onCreateProgramClick = onCreateProgramClick,
        onGenerateProgramClick = onGenerateProgramClick,
        onProgramLibraryClick = onProgramLibraryClick,
        onStartNextWorkoutClick = onStartNextWorkoutClick,
        onStartEmptyWorkoutClick = onStartEmptyWorkoutClick,
        onStartWorkoutClick = onStartWorkoutClick,
        onSkipWorkoutCheckedChange = onSkipWorkoutCheckedChange,
        onSwapLiftClick = { target ->
            onSwapLiftClick(target.workoutLiftId, target.liftId, target.name)
        },
        modifier = modifier,
        isPreviewMode = isPreviewMode,
        onKeepProgramClick = onKeepProgramClick,
        onDiscardProgramClick = onDiscardProgramClick,
    )
}

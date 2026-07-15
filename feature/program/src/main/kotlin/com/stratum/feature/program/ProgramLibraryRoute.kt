package com.stratum.feature.program

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.feature.program.ui.ProgramLibraryScreen

@Composable
fun ProgramLibraryRoute(
    onBackClick: () -> Unit,
    onProgramClick: (programId: String) -> Unit,
    onWorkoutClick: (programId: String, workoutId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProgramLibraryViewModel = hiltViewModel(),
) {
    val restDayLabel = stringResource(R.string.program_library_rest_day)
    LaunchedEffect(restDayLabel) {
        viewModel.setRestDayLabel(restDayLabel)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProgramLibraryScreen(
        state = state,
        onBackClick = onBackClick,
        onProgramClick = onProgramClick,
        onWorkoutClick = onWorkoutClick,
        onActivateClick = viewModel::activateProgram,
        onExpandToggle = viewModel::toggleExpanded,
        onDeleteRequest = viewModel::requestDelete,
        onDeleteConfirm = viewModel::confirmDelete,
        onDeleteDismiss = viewModel::dismissDelete,
        onActivateConfirm = viewModel::confirmActivateDiscardingSession,
        onActivateDismiss = viewModel::dismissActivate,
        onUserMessageShown = viewModel::onUserMessageShown,
        modifier = modifier,
    )
}

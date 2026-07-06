package com.stratum.feature.program

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.feature.program.ui.ProgramsScreen
import com.stratum.feature.program.ui.ProgramsScreenCallbacks
import com.stratum.feature.program.ui.ProgramsUiEvent

@Composable
fun ProgramsRoute(
    viewModel: ProgramsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
    callbacks: ProgramsScreenCallbacks = ProgramsScreenCallbacks(),
    onSwapLiftClick: (workoutLiftId: String, liftId: String, liftName: String) -> Unit = { _, _, _ -> },
    onNavigateToWorkout: (workoutId: String) -> Unit = {},
    isPreviewMode: Boolean = false,
) {
    val state by viewModel.programsUiState.collectAsStateWithLifecycle()
    val activeWorkoutBar by viewModel.activeWorkoutBarState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, onNavigateToWorkout) {
        viewModel.events.collect { event ->
            when (event) {
                is ProgramsUiEvent.NavigateToWorkout -> onNavigateToWorkout(event.workoutId)
                is ProgramsUiEvent.ShowMessage -> Unit
            }
        }
    }

    ProgramsScreen(
        state = state,
        activeWorkoutBar = activeWorkoutBar,
        callbacks =
            callbacks.copy(
                onStartNextWorkoutClick = viewModel::startNextWorkout,
                onStartEmptyWorkoutClick = viewModel::startEmptyWorkout,
                onStartWorkoutClick = viewModel::startProgramWorkout,
            ),
        onSwapLiftClick = { target ->
            onSwapLiftClick(target.workoutLiftId, target.liftId, target.name)
        },
        onCancelWorkoutClick = viewModel::abandonActiveSession,
        onExpandWorkoutClick = onNavigateToWorkout,
        modifier = modifier,
        isPreviewMode = isPreviewMode,
    )
}

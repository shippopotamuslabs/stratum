package com.stratum.feature.workout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.feature.workout.WorkoutViewModel

import com.stratum.core.ui.component.SupersetLinkDirection

/**
 * Compose route for the live workout screen (Slice 7). Navigation is hoisted to the
 * route level via callbacks — the navigation controller is never threaded into the
 * feature (ARCHITECTURE.md §4.5).
 */
@Composable
fun WorkoutRoute(
    workoutId: String,
    onNavigateBack: () -> Unit,
    onNavigateToFinalize: (sessionId: String) -> Unit,
    onSwapLiftClick: (workoutId: String, workoutLiftId: String?, liftId: String?, liftName: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(workoutId) {
        viewModel.startSession(WorkoutId(workoutId))
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is WorkoutUiEvent.NavigateToFinalize -> onNavigateToFinalize(event.sessionId)
                is WorkoutUiEvent.ShowMessage -> Unit
            }
        }
    }

    when (val state = uiState) {
        is WorkoutUiState.Loading -> LoadingContent()
        is WorkoutUiState.NoActiveSession -> NoActiveSessionContent(onNavigateBack)
        is WorkoutUiState.Error -> ErrorContent(state.message, onNavigateBack)
        is WorkoutUiState.Loaded -> WorkoutScreen(
            state = state,
            onNavigateBack = onNavigateBack,
            onFinalize = viewModel::finalizeSession,
            onAbandon = viewModel::abandonSession,
            onLogSet = viewModel::logSet,
            onUnlogSet = viewModel::unlogSet,
            onSwapLiftClick = onSwapLiftClick,
            onLinkSupersetWithPrevious = { liftId -> viewModel.linkSuperset(WorkoutLiftId(liftId), SupersetLinkDirection.Previous) },
            onLinkSupersetWithNext = { liftId -> viewModel.linkSuperset(WorkoutLiftId(liftId), SupersetLinkDirection.Next) },
            onRemoveFromSuperset = { liftId -> viewModel.removeFromSuperset(WorkoutLiftId(liftId)) },
            onSetUseRestTimerBetweenLifts = { liftId, enabled -> viewModel.setUseRestTimerBetweenLifts(WorkoutLiftId(liftId), enabled) },
            onRemoveLift = { liftId -> viewModel.removeLift(WorkoutLiftId(liftId)) },
            modifier = modifier
        )
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun NoActiveSessionContent(onNavigateBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StratumText(text = "No active session.")
        Spacer(modifier = Modifier.height(16.dp))
        StratumButton(text = "Back", onClick = onNavigateBack)
    }
}

@Composable
private fun ErrorContent(message: String, onNavigateBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StratumText(
            text = "Error: $message",
            style = StratumThemeAccessor.typography.bodyLarge.copy(color = StratumThemeAccessor.colors.error),
        )
        Spacer(modifier = Modifier.height(16.dp))
        StratumButton(text = "Back", onClick = onNavigateBack)
    }
}

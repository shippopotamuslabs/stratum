package com.stratum.feature.workout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.feature.workout.WorkoutViewModel

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
        is WorkoutUiState.Loaded -> LoadedContent(state, onNavigateBack, viewModel::finalizeSession, modifier)
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
        StratumButton(text = "Back", onClick = onNavigateBack)
    }
}

@Composable
private fun LoadedContent(
    state: WorkoutUiState.Loaded,
    onNavigateBack: () -> Unit,
    onFinalize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StratumScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { StratumTopBar(title = state.workoutName) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.lifts, key = { it.workoutLiftId }) { lift ->
                    StratumText(
                        text = lift.liftName,
                        style = StratumThemeAccessor.typography.titleLarge,
                    )
                    StratumText(
                        text = lift.progressionSchemeLabel,
                        style = StratumThemeAccessor.typography.sectionLabel,
                        muted = true,
                    )
                    lift.sets.forEach { set ->
                        SetRow(set)
                        HorizontalDivider()
                    }
                }
            }
            StratumButton(text = "Finalize", onClick = onFinalize)
            StratumButton(text = "Back", onClick = onNavigateBack)
        }
    }
}

@Composable
private fun SetRow(set: RenderedSetUi) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        StratumText(text = "${set.positionLabel} · ${set.setTypeLabel}")
        StratumText(
            text = "Rec: ${set.recommendedWeightLabel} × ${set.recommendedRepsLabel}",
            style = StratumThemeAccessor.typography.dataLarge,
        )
        set.loggedWeightLabel?.let { logged ->
            StratumText(
                text = "Logged: $logged × ${set.loggedRepsLabel}",
                style = StratumThemeAccessor.typography.dataLarge.copy(
                    color = StratumThemeAccessor.colors.accent,
                ),
            )
        }
    }
}


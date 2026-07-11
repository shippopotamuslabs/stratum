package com.stratum.feature.workout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.UserMessageEffect
import com.stratum.feature.workout.WorkoutCompleteViewModel

@Composable
fun WorkoutCompleteRoute(
    sessionId: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutCompleteViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(sessionId) {
        viewModel.loadSummary(sessionId)
    }

    when (val state = uiState) {
        is WorkoutCompleteUiState.Loading ->
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        is WorkoutCompleteUiState.Error ->
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StratumText(text = "Failed to load workout summary.")
                Spacer(modifier = Modifier.height(16.dp))
            }
        is WorkoutCompleteUiState.Loaded -> {
            UserMessageEffect(
                userMessage = state.userMessage,
                snackbarHostState = snackbarHostState,
                onUserMessageShown = viewModel::userMessageShown,
            )
            WorkoutCompleteScreen(
                state = state,
                onClose = onClose,
                modifier = modifier,
                snackbarHostState = snackbarHostState,
                onAcceptProposal = viewModel::onAcceptPrimary,
                onAcceptAlternative = viewModel::onAcceptAlternative,
                onSkipProposal = viewModel::onSkipProposal,
                onNotNowProposal = viewModel::onNotNowProposal,
                onConfirmBlastRadius = viewModel::onConfirmAccept,
                onDismissBlastRadius = viewModel::onDismissConfirm,
            )
        }
    }
}

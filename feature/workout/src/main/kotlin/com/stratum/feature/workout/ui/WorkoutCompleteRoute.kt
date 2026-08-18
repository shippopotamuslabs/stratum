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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.LocalUiRenderErrorReporter
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.UserMessageEffect
import com.stratum.core.ui.share.shareWorkoutImage
import com.stratum.feature.workout.R
import com.stratum.feature.workout.WorkoutCompleteViewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

@Composable
fun WorkoutCompleteRoute(
    sessionId: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutCompleteViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSharing by remember { mutableStateOf(false) }
    val shareFailedMessage = stringResource(CoreUiR.string.workout_share_failed)
    val chooserTitle = stringResource(CoreUiR.string.share_chooser_title)
    val renderErrorReporter = LocalUiRenderErrorReporter.current

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
                isSharing = isSharing,
                onShareCaptureFailed = {
                    scope.launch {
                        snackbarHostState.showSnackbar(shareFailedMessage)
                    }
                },
                onShareImage = { imageBitmap ->
                    if (isSharing) return@WorkoutCompleteScreen
                    isSharing = true
                    scope.launch {
                        try {
                            shareWorkoutImage(
                                context = context,
                                imageBitmap = imageBitmap,
                                sessionId = sessionId,
                                chooserTitle = chooserTitle,
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            renderErrorReporter.report(e)
                            snackbarHostState.showSnackbar(shareFailedMessage)
                        } finally {
                            isSharing = false
                        }
                    }
                },
                onAcceptProposal = viewModel::onAcceptPrimary,
                onAcceptAlternative = viewModel::onAcceptAlternative,
                onAcceptRepRangeShift = viewModel::onAcceptRepRangeShift,
                onSkipProposal = viewModel::onSkipProposal,
                onNotNowProposal = viewModel::onNotNowProposal,
                onConfirmBlastRadius = viewModel::onConfirmAccept,
                onDismissBlastRadius = viewModel::onDismissConfirm,
            )
        }
    }
}

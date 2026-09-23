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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    onEditEquipmentClick: (EquipmentEditRequest) -> Unit = {},
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
    // Refresh equipment cards on every return to this entry (back from the increments
    // editor, or foregrounding after editing tiers elsewhere): a pure re-analysis
    // against now-current tiers, so a saved fix is reflected and a cancelled edit
    // truthfully re-surfaces. The initial composition's resume is skipped; a recreated
    // route reloads via loadSummary regardless. Plain remember (not saveable) is enough
    // — worst case after process death is one missed refresh, and loadSummary covers it.
    var initialResume by remember { mutableStateOf(true) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, sessionId) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    if (initialResume) {
                        initialResume = false
                    } else {
                        viewModel.refreshEquipmentProposals(sessionId)
                    }
                }
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is WorkoutCompleteEvent.OpenEquipmentEditor ->
                    onEditEquipmentClick(event.request)
            }
        }
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
                actions =
                    WorkoutCompleteActions(
                        onClose = onClose,
                        onShareCaptureFailed = {
                            scope.launch {
                                snackbarHostState.showSnackbar(shareFailedMessage)
                            }
                        },
                        onShareImage = sharing@{ imageBitmap ->
                            if (isSharing) return@sharing
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
                        onEditProposal = viewModel::onEditProposal,
                        onSkipProposal = viewModel::onSkipProposal,
                        onNotNowProposal = viewModel::onNotNowProposal,
                        onConfirmBlastRadius = viewModel::onConfirmAccept,
                        onDismissBlastRadius = viewModel::onDismissConfirm,
                    ),
                modifier = modifier,
                snackbarHostState = snackbarHostState,
                isSharing = isSharing,
            )
        }
    }
}

package com.stratum.feature.history.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.LocalUiRenderErrorReporter
import com.stratum.core.ui.component.UserMessageEffect
import com.stratum.core.ui.share.shareWorkoutImage
import com.stratum.feature.history.WorkoutSessionDetailViewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

@Composable
fun WorkoutSessionDetailRoute(
    sessionId: String,
    onBackClick: () -> Unit,
    onEditWorkoutClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutSessionDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSharing by remember { mutableStateOf(false) }
    val shareFailedMessage = stringResource(CoreUiR.string.workout_share_failed)
    val chooserTitle = stringResource(CoreUiR.string.share_chooser_title)
    val renderErrorReporter = LocalUiRenderErrorReporter.current

    DisposableEffect(lifecycleOwner, sessionId) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.load(sessionId)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    UserMessageEffect(
        userMessage = userMessage,
        snackbarHostState = snackbarHostState,
        onUserMessageShown = viewModel::userMessageShown,
    )
    WorkoutSessionDetailScreen(
        state = uiState,
        onBackClick = onBackClick,
        onEditWorkoutClick = onEditWorkoutClick,
        onUpdateWorkoutTimes = viewModel::updateWorkoutTimes,
        modifier = modifier,
        isSharing = isSharing,
        onShareCaptureFailed = {
            scope.launch {
                snackbarHostState.showSnackbar(shareFailedMessage)
            }
        },
        onShareImage = { imageBitmap ->
            if (isSharing) return@WorkoutSessionDetailScreen
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
        snackbarHostState = snackbarHostState,
        onRetry = { viewModel.load(sessionId) },
    )
}

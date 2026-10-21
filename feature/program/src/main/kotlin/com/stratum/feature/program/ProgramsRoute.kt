package com.stratum.feature.program

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.component.UserMessageEffect
import com.stratum.feature.program.ui.CheckInScreen
import com.stratum.feature.program.ui.ProgramsScreen
import com.stratum.feature.program.ui.ProgramsScreenCallbacks
import com.stratum.feature.program.ui.ProgramsUiEvent
import dev.chrisbanes.haze.HazeState

@Composable
fun ProgramsRoute(
    viewModel: ProgramsViewModel = hiltViewModel(),
    checkInViewModel: CheckInViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
    callbacks: ProgramsScreenCallbacks = ProgramsScreenCallbacks(),
    onSwapLiftClick: (workoutId: String, workoutLiftId: String?, liftId: String?, liftName: String?) -> Unit = { _, _, _, _ -> },
    onNavigateToWorkout: (workoutId: String) -> Unit = {},
    isPreviewMode: Boolean = false,
    hazeState: HazeState? = null,
) {
    val state by viewModel.programsUiState.collectAsStateWithLifecycle()
    val activeWorkoutBar by viewModel.activeWorkoutBarState.collectAsStateWithLifecycle()
    val expandedWorkoutId by viewModel.expandedWorkoutId.collectAsStateWithLifecycle()
    val showCheckInBanner by checkInViewModel.showBanner.collectAsStateWithLifecycle()
    val checkInUiState by checkInViewModel.uiState.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel, onNavigateToWorkout) {
        viewModel.events.collect { event ->
            when (event) {
                is ProgramsUiEvent.NavigateToWorkout -> onNavigateToWorkout(event.workoutId)
            }
        }
    }

    UserMessageEffect(
        userMessage = userMessage,
        snackbarHostState = snackbarHostState,
        onUserMessageShown = viewModel::userMessageShown,
    )

    if (checkInUiState.isOpen) {
        CheckInScreen(
            state = checkInUiState,
            onBack = checkInViewModel::goBack,
            onOverallRecoverySelected = checkInViewModel::setOverallRecovery,
            onHasLocalizedIssuesSelected = checkInViewModel::setHasLocalizedIssues,
            onToggleFlaggedMuscle = checkInViewModel::toggleFlaggedMuscle,
            onMuscleSignalSelected = checkInViewModel::setMuscleSignal,
            onNext = checkInViewModel::goNext,
            onToggleAccepted = checkInViewModel::setAccepted,
            onOpenSwapPicker = checkInViewModel::openSwapPicker,
            onSelectSwapCandidate = checkInViewModel::selectSwapCandidate,
            onDismissSwapPicker = checkInViewModel::dismissSwapPicker,
            modifier = modifier,
        )
    } else {
        ProgramsScreen(
            state = state,
            activeWorkoutBar = activeWorkoutBar,
            expandedWorkoutId = expandedWorkoutId,
            callbacks =
                callbacks.copy(
                    onStartNextWorkoutClick = viewModel::startNextWorkout,
                    onStartEmptyWorkoutClick = viewModel::startEmptyWorkout,
                    onStartWorkoutClick = viewModel::startProgramWorkout,
                    onSkipWorkoutCheckedChange = { workoutId, checked ->
                        if (checked) viewModel.skipWorkout(workoutId) else viewModel.unskipWorkout(workoutId)
                    },
                    onSkipRestDayCheckedChange = { slotIndex, checked ->
                        if (checked) viewModel.skipRestDay(slotIndex) else viewModel.unskipRestDay(slotIndex)
                    },
                    onConfirmDiscardAndStart = viewModel::confirmDiscardAndStart,
                    onExpandedWorkoutChange = viewModel::setExpandedWorkout,
                ),
            onSwapLiftClick = { workoutId, target ->
                onSwapLiftClick(workoutId, target.workoutLiftId, target.liftId, target.name)
            },
            onCancelWorkoutClick = viewModel::abandonActiveSession,
            onExpandWorkoutClick = onNavigateToWorkout,
            modifier = modifier,
            isPreviewMode = isPreviewMode,
            showCheckInBanner = showCheckInBanner && !isPreviewMode,
            onCheckInBannerClick = checkInViewModel::openCheckIn,
            onCheckInSkipClick = checkInViewModel::skip,
            snackbarHostState = snackbarHostState,
            hazeState = hazeState,
        )
    }
}

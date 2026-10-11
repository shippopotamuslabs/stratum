package com.stratum.feature.workout.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.SupersetLinkDirection
import com.stratum.core.ui.component.UserMessageEffect
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.domain.lift.Weight
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.feature.workout.LiveSetKind
import com.stratum.feature.workout.WorkoutViewModel
import com.stratum.feature.workout.notification.RestTimerDefaults
import com.stratum.feature.workout.notification.RestTimerState
import kotlinx.coroutines.delay

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
    onEditEquipmentClick: (EquipmentEditRequest) -> Unit,
    onReorderLiftsClick: (workoutId: String) -> Unit,
    onCreateEquipmentClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val restState by viewModel.restTimerState.collectAsStateWithLifecycle()
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* best-effort — in-app timer works without permission */ }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(restState) {
        while (restState is RestTimerState.Running) {
            nowMs = System.currentTimeMillis()
            if (viewModel.restTimer.remainingSeconds(nowMs) <= 0) {
                // Same completion gate as RestTimerForegroundService — one bell max.
                viewModel.restCompletion.completeIfDue()
                break
            }
            delay(250L)
        }
        nowMs = System.currentTimeMillis()
    }

    val running = restState as? RestTimerState.Running
    val remainingRestSeconds = if (running != null) {
        viewModel.restTimer.remainingSeconds(nowMs)
    } else {
        0
    }
    val totalRestSeconds = running?.totalSeconds ?: 0
    val isRestTimerActive = running != null && remainingRestSeconds > 0
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val equipmentSwap by viewModel.equipmentSwap.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingSetLogged by remember { mutableStateOf<WorkoutUiEvent.SetLogged?>(null) }
    var failedLogSlotKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(workoutId) {
        viewModel.startSession(WorkoutId(workoutId))
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is WorkoutUiEvent.NavigateToFinalize -> onNavigateToFinalize(event.sessionId)
                WorkoutUiEvent.NavigateBackAfterAbandon -> onNavigateBack()
                is WorkoutUiEvent.SetLogged -> pendingSetLogged = event
                is WorkoutUiEvent.SetAmended -> {
                    // Durable state is updating; seed will clear pending when labels match.
                }
                is WorkoutUiEvent.SetLogFailed -> failedLogSlotKey = event.slotKey
            }
        }
    }

    UserMessageEffect(
        userMessage = userMessage,
        snackbarHostState = snackbarHostState,
        onUserMessageShown = viewModel::userMessageShown,
    )

    when (val state = uiState) {
        is WorkoutUiState.Loading -> LoadingContent(snackbarHostState = snackbarHostState)
        is WorkoutUiState.NoActiveSession -> NoActiveSessionContent(
            onNavigateBack = onNavigateBack,
            snackbarHostState = snackbarHostState,
        )
        is WorkoutUiState.Error -> ErrorContent(
            message = state.message,
            onNavigateBack = onNavigateBack,
            snackbarHostState = snackbarHostState,
        )
        is WorkoutUiState.Loaded -> {
            val restChrome = RestTimerChrome(
                isActive = isRestTimerActive,
                remainingSeconds = remainingRestSeconds,
                totalSeconds = totalRestSeconds,
                onStart = { seconds ->
                    viewModel.restTimer.start(
                        seconds = seconds.coerceAtMost(RestTimerDefaults.MAX_REST_SECONDS),
                        workoutId = state.workoutId,
                        workoutName = state.workoutName,
                    )
                },
                onAdd10s = viewModel.restTimer::add10Seconds,
                onSubtract10s = viewModel.restTimer::subtract10Seconds,
                onSkip = viewModel.restTimer::skip,
            )
            WorkoutScreen(
                state = state,
                restTimer = restChrome,
                snackbarHostState = snackbarHostState,
                pendingSetLogged = pendingSetLogged,
                onSetLoggedConsumed = { pendingSetLogged = null },
                failedLogSlotKey = failedLogSlotKey,
                onFailedLogConsumed = { failedLogSlotKey = null },
                equipmentSwap = equipmentSwap,
                onEquipmentSwapQuery = viewModel::setEquipmentSwapQuery,
                onEquipmentSwapSelect = viewModel::selectSwapEquipment,
                onEquipmentSwapDismiss = viewModel::closeEquipmentSwap,
                onEquipmentCreateNew = {
                    viewModel.beginCreateEquipment()
                    onCreateEquipmentClick()
                },
                actions = WorkoutScreenActions(
                    onNavigateBack = onNavigateBack,
                    onCompleteWorkout = viewModel::completeWorkout,
                    onAbandon = viewModel::abandonSession,
                    onLogSet = { slotKey, weightAmountDisplay, reps, rir, partialReps ->
                        // UI magnitudes are display-unit; ViewModel converts to canonical Lb storage.
                        viewModel.logSet(
                            slotKey = slotKey,
                            weight = Weight(
                                weightAmountDisplay,
                                viewModel.currentDisplayUnit(),
                            ),
                            reps = reps,
                            rir = rir,
                            partialReps = partialReps,
                        )
                    },
                    onUnlogSet = viewModel::unlogSet,
                    onSwapLiftClick = onSwapLiftClick,
                    onEditEquipmentClick = onEditEquipmentClick,
                    // Pill tap resolves here (not in the screen): the routed base
                    // request comes from the rendered lift via equipmentEditRequestFor
                    // (single source of routing truth) and the session-backed seed
                    // prefills it — falling back to the base when inconclusive so a
                    // showing pill never dead-ends.
                    onAdjustEquipmentTap = { workoutLiftId ->
                        state.lifts.firstOrNull { it.workoutLiftId == workoutLiftId }?.let { lift ->
                            val base = equipmentEditRequestFor(lift)
                            val seed = viewModel.liveEquipmentSeed(workoutLiftId)
                            onEditEquipmentClick(
                                seed?.let {
                                    liveEquipmentEditRequestFor(
                                        base = base,
                                        currentTiers = it.currentTiers,
                                        loadType = it.loadType,
                                        loggedWeights = it.loggedWeights,
                                    )
                                } ?: base,
                            )
                        }
                    },
                    onDismissEquipmentAdjust = viewModel::dismissEquipmentAdjust,
                    onEquipmentSwapClick = viewModel::openEquipmentSwap,
                    onLinkSupersetWithPrevious = { liftId ->
                        viewModel.linkSuperset(WorkoutLiftId(liftId), SupersetLinkDirection.Previous)
                    },
                    onLinkSupersetWithNext = { liftId ->
                        viewModel.linkSuperset(WorkoutLiftId(liftId), SupersetLinkDirection.Next)
                    },
                    onRemoveFromSuperset = { liftId ->
                        viewModel.removeFromSuperset(WorkoutLiftId(liftId))
                    },
                    onSetUseRestTimerBetweenLifts = { liftId, enabled ->
                        viewModel.setUseRestTimerBetweenLifts(WorkoutLiftId(liftId), enabled)
                    },
                    onRemoveLift = { liftId -> viewModel.removeLift(WorkoutLiftId(liftId)) },
                    onReorderLiftsClick = {
                        viewModel.startReorderLifts()
                        onReorderLiftsClick(workoutId)
                    },
                    onAddSet = viewModel::addSetForLift,
                    onDeleteSet = viewModel::deleteSetByKey,
                    onAddLift = {
                        onSwapLiftClick(state.workoutId, null, null, null)
                    },
                    onSetRestSecondsForSlots = viewModel::setRestSecondsForSlots,
                    onSetSetKind = { slotKey, kind ->
                        viewModel.setSetKind(slotKey, kind.toLiveSetKind())
                    },
                    liftNotes =
                        WorkoutLiftNotesActions(
                            onLoadNoteLists = viewModel::noteLists,
                            onSave = viewModel::saveLiftNotes,
                            onLoadPastNotes = viewModel::pastSessionNotes,
                        ),
                ),
                modifier = modifier,
            )
        }
    }
}

private fun WorkoutSetKind.toLiveSetKind(): LiveSetKind =
    when (this) {
        WorkoutSetKind.Standard -> LiveSetKind.Standard
        WorkoutSetKind.MyoRepFloor -> LiveSetKind.MyoRepFloor
        WorkoutSetKind.MyoRepMatch -> LiveSetKind.MyoRepMatch
        WorkoutSetKind.DropSet -> LiveSetKind.DropSet
    }

// Internal (not private) so screenshot previews render the real route states (STR-11).
@Composable
internal fun LoadingContent(snackbarHostState: SnackbarHostState) {
    StratumScaffold(snackbarHostState = snackbarHostState) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
internal fun NoActiveSessionContent(
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    StratumScaffold(snackbarHostState = snackbarHostState) {
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
}

@Composable
internal fun ErrorContent(
    message: String,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    StratumScaffold(snackbarHostState = snackbarHostState) {
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
}

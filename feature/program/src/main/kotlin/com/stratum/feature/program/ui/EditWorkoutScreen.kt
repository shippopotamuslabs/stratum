package com.stratum.feature.program.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.BackHandler
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.EquipmentSwapSheet
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumEdgeToEdgeCard
import com.stratum.core.ui.component.StratumEquipmentSubheader
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumPillToggle
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBarTextButton
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.component.UserMessageEffect
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.component.RirTargetBottomSheet
import com.stratum.core.ui.component.StratumMuscleVolumeCardState
import com.stratum.core.ui.component.StratumScopedMuscleVolumeRow
import com.stratum.core.ui.component.workoutlog.OverlayDockDefaultRevealMargin
import com.stratum.core.ui.component.workoutlog.rememberOverlayDockListBinding
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.ProgressionScheme
import com.stratum.feature.program.EditWorkoutViewModel
import com.stratum.feature.program.R
import com.stratum.core.ui.component.SupersetLinkDirection
import com.stratum.core.ui.component.shouldKeepSupersetSheetOpenAfterLink
import com.stratum.core.ui.component.SupersetLinkBottomSheet
import com.stratum.core.ui.component.LiftActionsBottomSheet
import com.stratum.core.ui.component.SupersetGroupChip
import com.stratum.core.ui.component.supersetLinkSheetOptions
import com.stratum.core.ui.component.isLastInSupersetChain
import com.stratum.core.ui.component.isSupersetPairMember
import com.stratum.feature.program.formatEstimatedWorkoutDuration

@Composable
fun EditWorkoutRoute(
    workoutId: String,
    onBackClick: () -> Unit,
    onSwapLiftClick: (workoutId: String, workoutLiftId: String?, liftId: String?, liftName: String?) -> Unit,
    onReorderLiftsClick: (workoutId: String) -> Unit,
    onNavigateToWorkout: (workoutId: String) -> Unit = {},
    onCreateEquipmentClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    programId: String? = null,
    viewModel: EditWorkoutViewModel = hiltViewModel(),
) {
    LaunchedEffect(workoutId, programId) {
        viewModel.initialize(workoutId, programId)
    }

    // Do not clearSession() on composition dispose. Nav3 single-pane only composes the top
    // entry; covering EditWorkout with LiftSwap/ReorderLifts would dispose this route while
    // the entry-scoped ViewModel stays alive — wiping targetWorkoutId drops WorkoutEditSession
    // events. Permanent leave destroys the ViewModel via the entry ViewModelStore.

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val isStarting by viewModel.isStarting.collectAsStateWithLifecycle()
    val equipmentSwap by viewModel.equipmentSwap.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLeaveDiscardDialog by remember { mutableStateOf(false) }
    var showDirtyStartDialog by remember { mutableStateOf(false) }
    var showActiveSessionDiscardDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel, onNavigateToWorkout) {
        viewModel.events.collect { event ->
            when (event) {
                is EditWorkoutUiEvent.NavigateToWorkout -> onNavigateToWorkout(event.workoutId)
                EditWorkoutUiEvent.ShowActiveSessionDiscardDialog ->
                    showActiveSessionDiscardDialog = true
            }
        }
    }

    UserMessageEffect(
        userMessage = userMessage,
        snackbarHostState = snackbarHostState,
        onUserMessageShown = viewModel::userMessageShown,
    )

    EditWorkoutScreen(
        state = state,
        showDiscardDialog = showLeaveDiscardDialog,
        onDismissDiscardDialog = { showLeaveDiscardDialog = false },
        onConfirmDiscard = {
            showLeaveDiscardDialog = false
            onBackClick()
        },
        // Fresh read after dock commit so back sees newly dirtied state.
        isWorkoutDirty = {
            (viewModel.uiState.value as? EditWorkoutUiState.Loaded)?.isDirty == true
        },
        onLeave = onBackClick,
        onRequestDiscardDialog = { showLeaveDiscardDialog = true },
        // Library edits pass programId; only active-program templates can start a session.
        showStartWorkout = programId == null,
        isStarting = isStarting,
        callbacks =
            EditWorkoutScreenCallbacks(
                onAddSet = viewModel::addSet,
                onRemoveSet = viewModel::removeSet,
                onAddLift = { viewModel.addLift(onSwapLiftClick) },
                onRemoveLift = viewModel::removeLift,
                onLinkSupersetWithPrevious = { viewModel.linkSuperset(it, SupersetLinkDirection.Previous) },
                onLinkSupersetWithNext = { viewModel.linkSuperset(it, SupersetLinkDirection.Next) },
                onRemoveFromSuperset = viewModel::removeFromSuperset,
                onSetRestSeconds = { id, value ->
                    viewModel.setRestSeconds(setId = id, workoutLiftId = null, restSeconds = value)
                },
                onSetRestSecondsForAllSetsOnLift = { id, value ->
                    viewModel.setRestSeconds(setId = null, workoutLiftId = id, restSeconds = value)
                },
                onSetRepBand = { id, min, max -> viewModel.setReps(id, min = min, max = max) },
                onSetRirGoal = viewModel::setRirGoal,
                onSetUseRestTimerBetweenLifts = viewModel::setUseRestTimerBetweenLifts,
                onSetProgressionScheme = viewModel::setProgressionScheme,
                onSetSetKind = viewModel::setSetKind,
                onSetMatchBackoffCount = viewModel::setMatchBackoffCount,
                onSaveWorkout = {
                    viewModel.saveWorkout(onComplete = onBackClick)
                },
                onStartWorkout = {
                    val loaded = viewModel.uiState.value as? EditWorkoutUiState.Loaded
                    if (loaded != null) {
                        if (loaded.isDirty) {
                            showDirtyStartDialog = true
                        } else {
                            viewModel.requestStartWorkout()
                        }
                    }
                },
                onSwapLiftClick = onSwapLiftClick,
                onReorderLiftsClick = {
                    viewModel.startReorderLifts()
                    onReorderLiftsClick(workoutId)
                },
                onEquipmentSwapClick = viewModel::openEquipmentSwap,
            ),
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )

    if (showDirtyStartDialog) {
        EditWorkoutDirtyStartDialog(
            onConfirmSaveAndStart = {
                showDirtyStartDialog = false
                viewModel.startAfterDirtyResolution(saveEdits = true)
            },
            onDiscardAndStart = {
                showDirtyStartDialog = false
                viewModel.startAfterDirtyResolution(saveEdits = false)
            },
            onDismiss = { showDirtyStartDialog = false },
        )
    }

    if (showActiveSessionDiscardDialog) {
        StartSessionDiscardDialog(
            onDismiss = { showActiveSessionDiscardDialog = false },
            onConfirm = {
                showActiveSessionDiscardDialog = false
                viewModel.confirmDiscardActiveAndStart()
            },
        )
    }

    equipmentSwap?.let { swap ->
        EquipmentSwapSheet(
            state = swap.sheet,
            onQueryChange = viewModel::setEquipmentSwapQuery,
            onSelect = { viewModel.selectSwapEquipment(it.id) },
            onCreateNew = {
                viewModel.beginCreateEquipment()
                onCreateEquipmentClick()
            },
            onDismissRequest = viewModel::closeEquipmentSwap,
        )
    }
}

@Composable
fun EditWorkoutScreen(
    state: EditWorkoutUiState,
    callbacks: EditWorkoutScreenCallbacks,
    modifier: Modifier = Modifier,
    showDiscardDialog: Boolean = false,
    onDismissDiscardDialog: () -> Unit = {},
    onConfirmDiscard: () -> Unit = {},
    /** Fresh dirty check after dock commit (reads ViewModel, not composition snapshot). */
    isWorkoutDirty: () -> Boolean = {
        (state as? EditWorkoutUiState.Loaded)?.isDirty == true
    },
    /** Navigate away after clean leave or confirmed discard. */
    onLeave: () -> Unit = {},
    onRequestDiscardDialog: () -> Unit = {},
    /** Active-program edit only; hidden for library edits and create-program embed. */
    showStartWorkout: Boolean = false,
    isStarting: Boolean = false,
    snackbarHostState: SnackbarHostState? = null,
) {
    var rirSheetSet by remember { mutableStateOf<EditSetState?>(null) }
    var supersetSheetLiftId by remember { mutableStateOf<String?>(null) }
    var liftActionsLiftId by remember { mutableStateOf<String?>(null) }
    var showProgressionSchemeExplainer by remember { mutableStateOf(false) }
    val dockHost = remember { EditWorkoutDockHostState() }

    fun attemptLeave() {
        if (!dockHost.tryCommitPendingOrBlock()) return
        if (isWorkoutDirty()) {
            onRequestDiscardDialog()
        } else {
            onLeave()
        }
    }

    fun attemptSave() {
        if (!dockHost.tryCommitPendingOrBlock()) return
        callbacks.onSaveWorkout()
    }

    fun attemptStart() {
        if (isStarting) return
        if (!dockHost.tryCommitPendingOrBlock()) return
        callbacks.onStartWorkout()
    }

    // Dock open: system back tries commit/block first (also registered inside ProvideEditWorkoutDock).
    // When dock closed: dirty → discard dialog; clean → leave.
    BackHandler(enabled = state is EditWorkoutUiState.Loaded && !dockHost.isOpen) {
        attemptLeave()
    }

    ProvideEditWorkoutDock(
        modifier = modifier.fillMaxSize(),
        host = dockHost,
    ) { listModifier, _ ->
        StratumScaffold(
            modifier = listModifier,
            snackbarHostState = snackbarHostState,
            topBar = {
                EditWorkoutTopBar(
                    state = state,
                    onBackClick = { attemptLeave() },
                    onSaveWorkout = { attemptSave() },
                )
            },
        ) { padding ->
            when (state) {
                EditWorkoutUiState.Loading ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        StratumText(
                            text = stringResource(R.string.edit_workout_loading),
                            muted = true,
                        )
                    }

                EditWorkoutUiState.NotFound ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        StratumText(
                            text = stringResource(R.string.edit_workout_not_found),
                            muted = true,
                        )
                    }

                is EditWorkoutUiState.Loaded ->
                    EditWorkoutContent(
                        state = state,
                        callbacks = callbacks,
                        onRirClick = { set -> rirSheetSet = set },
                        onShowLiftActions = { liftId -> liftActionsLiftId = liftId },
                        onShowProgressionSchemeInfo = { showProgressionSchemeExplainer = true },
                        modifier = Modifier.padding(padding),
                        dockHost = dockHost,
                        showStartWorkout = showStartWorkout,
                        isStarting = isStarting,
                        onStartWorkout = { attemptStart() },
                    )
            }
        }
    }

    rirSheetSet?.let { targetSet ->
        RirTargetBottomSheet(
            selectedRirGoal = targetSet.rirGoal,
            onRirSelected = { rir -> callbacks.onSetRirGoal(targetSet.setId, rir) },
            onDismiss = { rirSheetSet = null },
        )
    }

    val loadedState = state as? EditWorkoutUiState.Loaded
    supersetSheetLiftId?.let { liftId ->
        val loaded = loadedState ?: return@let
        val currentLift = loaded.lifts.firstOrNull { lift -> lift.workoutLiftId == liftId }
        val options = supersetLinkSheetOptions(lifts = loaded.lifts, workoutLiftId = liftId)
        if (options.isNotEmpty() && currentLift != null) {
            SupersetLinkBottomSheet(
                options = options,
                showRestBetweenLiftsToggle =
                    isSupersetPairMember(currentLift, loaded.lifts) &&
                        !isLastInSupersetChain(currentLift, loaded.lifts),
                useRestTimerBetweenLifts = currentLift.useRestTimerBetweenLifts,
                onUseRestTimerBetweenLiftsChange = { enabled ->
                    callbacks.onSetUseRestTimerBetweenLifts(liftId, enabled)
                },
                onOptionSelected = { direction ->
                    when (direction) {
                        SupersetLinkDirection.Previous -> callbacks.onLinkSupersetWithPrevious(liftId)
                        SupersetLinkDirection.Next -> callbacks.onLinkSupersetWithNext(liftId)
                        SupersetLinkDirection.Remove -> callbacks.onRemoveFromSuperset(liftId)
                    }
                    if (!shouldKeepSupersetSheetOpenAfterLink(loaded.lifts, liftId, direction)) {
                        supersetSheetLiftId = null
                    }
                },
                onDismiss = { supersetSheetLiftId = null },
            )
        } else {
            supersetSheetLiftId = null
        }
    }

    liftActionsLiftId?.let { liftId ->
        val loaded = loadedState ?: return@let
        val lift = loaded.lifts.firstOrNull { it.workoutLiftId == liftId } ?: return@let
        LiftActionsBottomSheet(
            onConfigureSuperset = {
                liftActionsLiftId = null
                supersetSheetLiftId = liftId
            },
            onSwapLift = {
                liftActionsLiftId = null
                callbacks.onSwapLiftClick(loaded.workoutId, liftId, lift.liftId, lift.name)
            },
            onRemoveLift = {
                liftActionsLiftId = null
                callbacks.onRemoveLift(liftId)
            },
            onReorderLifts = {
                liftActionsLiftId = null
                callbacks.onReorderLiftsClick()
            },
            onDismiss = { liftActionsLiftId = null },
        )
    }

    if (showDiscardDialog) {
        EditWorkoutDiscardBottomSheet(
            onConfirmDiscard = onConfirmDiscard,
            onDismiss = onDismissDiscardDialog,
        )
    }

    if (showProgressionSchemeExplainer) {
        ProgressionSchemeExplainerSheet(onDismiss = { showProgressionSchemeExplainer = false })
    }
}

@Composable
private fun EditWorkoutTopBar(
    state: EditWorkoutUiState,
    onBackClick: () -> Unit,
    onSaveWorkout: () -> Unit,
) {
    val title =
        when (state) {
            is EditWorkoutUiState.Loaded -> state.workoutName
            else -> stringResource(R.string.edit_workout_title)
        }

    StratumTopBar(
        title = title,
        navigationIcon = {
            StratumTopBarBackButton(
                onClick = onBackClick,
                contentDescription = stringResource(R.string.edit_workout_back),
            )
        },
        actions = {
            // Keep "Confirm" stable while saving: switching to "Saving…" flashes for a
            // frame or two before navigate-away and looks like a glitch. Show the same
            // label (disabled) until dirty/saving both clear, then the action disappears.
            if (state is EditWorkoutUiState.Loaded && (state.isDirty || state.isSaving)) {
                StratumTopBarTextButton(
                    text = stringResource(R.string.edit_workout_confirm),
                    onClick = onSaveWorkout,
                    enabled = !state.isSaving && state.saveBlockedReason == null,
                )
            }
        },
    )
}

/**
 * Workout lift editor body without scaffold/top bar — for embedding in Create Program.
 */
@Composable
internal fun EditWorkoutEmbeddedContent(
    state: EditWorkoutUiState.Loaded,
    callbacks: EditWorkoutScreenCallbacks,
    modifier: Modifier = Modifier,
    showVolume: Boolean = false,
) {
    var rirSheetSet by remember { mutableStateOf<EditSetState?>(null) }
    var liftActionsLiftId by remember { mutableStateOf<String?>(null) }
    var showProgressionSchemeExplainer by remember { mutableStateOf(false) }
    val dockHost = remember { EditWorkoutDockHostState() }

    // Embed torn down mid-edit: commit valid drafts; cannot block dispose for invalid.
    DisposableEffect(dockHost) {
        onDispose {
            dockHost.commitIfValidThenClose()
        }
    }

    ProvideEditWorkoutDock(
        modifier = modifier,
        host = dockHost,
    ) { listModifier, _ ->
        EditWorkoutContent(
            state = state,
            callbacks = callbacks,
            onRirClick = { set -> rirSheetSet = set },
            onShowLiftActions = { liftId -> liftActionsLiftId = liftId },
            onShowProgressionSchemeInfo = { showProgressionSchemeExplainer = true },
            modifier = listModifier,
            showVolume = showVolume,
            dockHost = dockHost,
        )
    }

    rirSheetSet?.let { targetSet ->
        RirTargetBottomSheet(
            selectedRirGoal = targetSet.rirGoal,
            onRirSelected = { rir -> callbacks.onSetRirGoal(targetSet.setId, rir) },
            onDismiss = { rirSheetSet = null },
        )
    }

    liftActionsLiftId?.let { liftId ->
        val lift = state.lifts.firstOrNull { it.workoutLiftId == liftId } ?: return@let
        LiftActionsBottomSheet(
            onConfigureSuperset = { liftActionsLiftId = null },
            onSwapLift = {
                liftActionsLiftId = null
                callbacks.onSwapLiftClick(state.workoutId, liftId, lift.liftId, lift.name)
            },
            onRemoveLift = {
                liftActionsLiftId = null
                callbacks.onRemoveLift(liftId)
            },
            onReorderLifts = {
                liftActionsLiftId = null
                callbacks.onReorderLiftsClick()
            },
            onDismiss = { liftActionsLiftId = null },
        )
    }

    if (showProgressionSchemeExplainer) {
        ProgressionSchemeExplainerSheet(onDismiss = { showProgressionSchemeExplainer = false })
    }
}

@Composable
internal fun EditWorkoutContent(
    state: EditWorkoutUiState.Loaded,
    callbacks: EditWorkoutScreenCallbacks,
    onRirClick: (EditSetState) -> Unit,
    onShowLiftActions: (workoutLiftId: String) -> Unit,
    onShowProgressionSchemeInfo: () -> Unit,
    dockHost: EditWorkoutDockHostState,
    modifier: Modifier = Modifier,
    showVolume: Boolean = true,
    showStartWorkout: Boolean = false,
    isStarting: Boolean = false,
    onStartWorkout: () -> Unit = callbacks.onStartWorkout,
) {
    // Horizontal padding only on inset chrome; lift cards are full-bleed like live workout.
    val listInset = Modifier.padding(horizontal = StratumSpacing.s12)
    val listState = rememberLazyListState()
    // Parent ProvideEditWorkoutDock already supplies listModifier-sized host; we only bind list.
    val dockHeightDp =
        rememberOverlayDockListBinding(
            listState = listState,
            dock = dockHost.overlay,
            revealMargin = OverlayDockDefaultRevealMargin,
        )

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding =
            PaddingValues(
                top = StratumSpacing.s16,
                bottom = StratumSpacing.s16 + dockHeightDp,
            ),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
    ) {
            if (showStartWorkout) {
                item {
                    StratumButton(
                        text = stringResource(R.string.programs_start_workout),
                        onClick = onStartWorkout,
                        enabled = !isStarting && !state.isSaving,
                        modifier = listInset.fillMaxWidth(),
                    )
                }
            }
            if (showVolume) {
                val workoutVolumeCards = state.upperVolumeByMuscle + state.lowerVolumeByMuscle
                val programVolumeCards =
                    state.programUpperVolumeByMuscle + state.programLowerVolumeByMuscle
                if (workoutVolumeCards.isNotEmpty() || programVolumeCards.isNotEmpty()) {
                    item {
                        StratumScopedMuscleVolumeRow(
                            workoutCards = workoutVolumeCards,
                            programCards = programVolumeCards,
                            title = stringResource(R.string.edit_workout_volume_title),
                            scopeLabels =
                                listOf(
                                    stringResource(R.string.edit_workout_volume_scope_workout),
                                    stringResource(R.string.edit_workout_volume_scope_program),
                                ),
                            modifier = listInset,
                        )
                    }
                }
            }
            item {
                EditWorkoutExerciseSummaryHeader(
                    exerciseCount = state.lifts.size,
                    estimatedDurationMinutes = state.estimatedDurationMinutes,
                    onAddLift = callbacks.onAddLift,
                    modifier = listInset,
                )
            }
            if (state.saveBlockedReason == SaveBlockedReason.PlaceholderLift) {
                item {
                    StratumText(
                        text = stringResource(R.string.edit_workout_save_blocked_placeholder_lift),
                        style = StratumThemeAccessor.typography.bodyLarge,
                        muted = true,
                        modifier = listInset,
                    )
                }
            }
            itemsIndexed(state.lifts, key = { _, lift -> lift.workoutLiftId }) { _, lift ->
                EditLiftPanel(
                    lift = lift,
                    allLifts = state.lifts,
                    onAddSet = { callbacks.onAddSet(lift.workoutLiftId) },
                    onRemoveSet = callbacks.onRemoveSet,
                    onShowLiftActions = { onShowLiftActions(lift.workoutLiftId) },
                    onRirClick = onRirClick,
                    onSetRepBand = callbacks.onSetRepBand,
                    onSetRestSeconds = callbacks.onSetRestSeconds,
                    onSetRestSecondsForAllSetsOnLift = callbacks.onSetRestSecondsForAllSetsOnLift,
                    onSetProgressionScheme = callbacks.onSetProgressionScheme,
                    onShowProgressionSchemeInfo = onShowProgressionSchemeInfo,
                    onSetKind = callbacks.onSetSetKind,
                    onSetMatchBackoffCount = callbacks.onSetMatchBackoffCount,
                    onEquipmentSwapClick =
                        callbacks.onEquipmentSwapClick?.let { onSwap ->
                            { onSwap(lift.workoutLiftId) }
                        },
                )
            }
        item {
            StratumOutlinedButton(
                text = stringResource(R.string.edit_workout_add_lift),
                onClick = callbacks.onAddLift,
                modifier = listInset.padding(bottom = StratumSpacing.s16).fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EditWorkoutExerciseSummaryHeader(
    exerciseCount: Int,
    estimatedDurationMinutes: Int,
    onAddLift: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
        ) {
            StratumText(
                text = stringResource(R.string.edit_workout_exercise_count_header, exerciseCount),
                style = StratumThemeAccessor.typography.titleLarge,
            )
            StratumText(
                text =
                    stringResource(
                        R.string.edit_workout_estimated_duration,
                        formatEstimatedWorkoutDuration(estimatedDurationMinutes),
                    ),
                muted = true,
            )
        }
        StratumIconButton(
            onClick = onAddLift,
            containerColor = StratumThemeAccessor.colors.surfaceRaised,
        ) {
            Icon(
                painter = painterResource(CoreUiR.drawable.ic_add),
                contentDescription = stringResource(R.string.edit_workout_add_lift),
                tint = StratumThemeAccessor.colors.textPrimary,
            )
        }
    }
}

@Composable
private fun EditLiftPanel(
    lift: EditLiftState,
    allLifts: List<EditLiftState>,
    onAddSet: () -> Unit,
    onRemoveSet: (setId: String) -> Unit,
    onShowLiftActions: () -> Unit,
    onRirClick: (EditSetState) -> Unit,
    onSetRepBand: (setId: String, repMin: Int, repMax: Int) -> Unit,
    onSetRestSeconds: (setId: String, restSeconds: Int) -> Unit,
    onSetRestSecondsForAllSetsOnLift: (workoutLiftId: String, restSeconds: Int) -> Unit,
    onSetProgressionScheme: (workoutLiftId: String, scheme: ProgressionScheme) -> Unit,
    onShowProgressionSchemeInfo: () -> Unit,
    onSetKind: (setId: String, kind: EditSetKind) -> Unit = { _, _ -> },
    onSetMatchBackoffCount: (activationSetId: String, count: Int) -> Unit = { _, _ -> },
    onEquipmentSwapClick: (() -> Unit)? = null,
) {
    StratumEdgeToEdgeCard(
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = StratumSpacing.s16),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StratumText(
                text = lift.name,
                style = StratumThemeAccessor.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            StratumIconButton(
                onClick = onShowLiftActions,
                containerColor = Color.Transparent,
            ) {
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.edit_workout_lift_menu),
                    tint = StratumThemeAccessor.colors.textPrimary,
                )
            }
        }

        onEquipmentSwapClick?.let { onSwapClick ->
            StratumEquipmentSubheader(
                equipmentName = lift.equipmentName,
                onSwapClick = onSwapClick,
            )
        }

        if (lift.isCompound) {
            ProgressionSchemeToggleRow(
                selectedScheme = lift.progressionScheme,
                onSchemeSelected = { scheme -> onSetProgressionScheme(lift.workoutLiftId, scheme) },
                onInfoClick = onShowProgressionSchemeInfo,
                modifier = Modifier.padding(horizontal = StratumSpacing.s16),
            )
        }

        if (isSupersetPairMember(lift, allLifts)) {
            lift.supersetGroupId?.let { groupId ->
                SupersetGroupChip(
                    groupId = groupId,
                    modifier = Modifier.padding(horizontal = StratumSpacing.s16),
                )
            }
        }

        EditWorkoutSetTable(
            workoutLiftId = lift.workoutLiftId,
            sets = lift.sets,
            progressionScheme = lift.progressionScheme,
            onAddSet = onAddSet,
            onRemoveSet = onRemoveSet,
            onRepBandChange = onSetRepBand,
            onSetRestSeconds = onSetRestSeconds,
            onSetRestSecondsForAllSetsOnLift = onSetRestSecondsForAllSetsOnLift,
            onRirClick = onRirClick,
            onSetKind = onSetKind,
            onSetMatchBackoffCount = onSetMatchBackoffCount,
            setKindEnabled = { setId, kind ->
                val index = lift.sets.indexOfFirst { it.setId == setId }
                index >= 0 && com.stratum.feature.program.canSelectSetKind(lift, index, kind)
            },
            showRestForSet = { setId ->
                val index = lift.sets.indexOfFirst { it.setId == setId }
                index >= 0 && com.stratum.feature.program.showRestForSet(lift, index)
            },
        )
    }
}

@Composable
private fun ProgressionSchemeToggleRow(
    selectedScheme: ProgressionScheme,
    onSchemeSelected: (ProgressionScheme) -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val segments =
        listOf(
            stringResource(R.string.edit_workout_progression_scheme_linear),
            stringResource(R.string.edit_workout_progression_scheme_double),
        )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            // §12.5: the label + info icon row floors at the 48dp target.
            modifier =
                Modifier
                    .sizeIn(minHeight = 48.dp)
                    .clickable(onClick = onInfoClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
        ) {
            StratumText(
                text = stringResource(R.string.edit_workout_progression_scheme_label),
                style = StratumThemeAccessor.typography.sectionLabel,
                muted = true,
            )
            Icon(
                painter = painterResource(CoreUiR.drawable.ic_info),
                contentDescription =
                    stringResource(R.string.edit_workout_progression_scheme_info_content_description),
                modifier = Modifier.size(16.dp),
                tint = StratumThemeAccessor.colors.textMuted,
            )
        }
        StratumPillToggle(
            segments = segments,
            selectedIndex = if (selectedScheme == ProgressionScheme.Linear) 0 else 1,
            onSelectedIndexChange = { index ->
                onSchemeSelected(
                    if (index == 0) ProgressionScheme.Linear else ProgressionScheme.Double,
                )
            },
            selectedColor = StratumThemeAccessor.colors.surfaceInset,
        )
    }
}

@Composable
private fun EditWorkoutDiscardBottomSheet(
    onConfirmDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    StratumBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        EditWorkoutDiscardSheetContent(
            onConfirmDiscard = onConfirmDiscard,
            onDismiss = onDismiss,
        )
    }
}

/** Confirm / discard unsaved template edits before starting a live session. */
@Composable
private fun EditWorkoutDirtyStartDialog(
    onConfirmSaveAndStart: () -> Unit,
    onDiscardAndStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        EditWorkoutDirtyStartDialogContent(
            onConfirmSaveAndStart = onConfirmSaveAndStart,
            onDiscardAndStart = onDiscardAndStart,
            onDismiss = onDismiss,
        )
    }
}

@Preview(name = "Edit workout loaded light")
@Composable
fun EditWorkoutScreenLoadedPreview() {
    StratumTheme {
        EditWorkoutScreen(
            showStartWorkout = true,
            state =
                EditWorkoutUiState.Loaded(
                    workoutId = "w-1",
                    workoutName = "Push Day",
                    upperVolumeByMuscle =
                        listOf(
                            StratumMuscleVolumeCardState(
                                muscle = MuscleGroup.Chest,
                                displayName = "Chest",
                                exerciseCount = 1,
                                setCount = 2.0,
                            ),
                        ),
                    lowerVolumeByMuscle = emptyList(),
                    estimatedDurationMinutes = 4,
                    lifts =
                        listOf(
                            EditLiftState(
                                workoutLiftId = "wl-1",
                                liftId = "bench-press",
                                name = "Barbell Bench Press",
                                position = 0,
                                sets =
                                    listOf(
                                        EditSetState(
                                            setId = "s-1",
                                            setNumber = 1,
                                            repMin = 5,
                                            repMax = 5,
                                            restSeconds = 120,
                                            rirGoal = 2,
                                            isTopSet = true,
                                        ),
                                        EditSetState(
                                            setId = "s-2",
                                            setNumber = 2,
                                            repMin = 0,
                                            repMax = 0,
                                            restSeconds = 120,
                                            rirGoal = 2,
                                            isTopSet = false,
                                        ),
                                    ),
                            ),
                        ),
                ),
            onLeave = {},
            // Explicit no-op keeps the subheader visible, matching the wired edit flow.
            callbacks = EditWorkoutScreenCallbacks(onEquipmentSwapClick = {}),
        )
    }
}

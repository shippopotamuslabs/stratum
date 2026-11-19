package com.stratum.feature.program.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.ProgramGenerationLoadingContent
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumConfirmDialog
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumReorderItem
import com.stratum.core.ui.component.StratumReorderListScreen
import com.stratum.core.ui.component.StratumDetailScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextField
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.progress.ProgramGenerationProgressUi
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.stratumCardHardShadow
import com.stratum.core.ui.theme.stratumDockTopRule
import com.stratum.core.ui.trainingsetup.MinimumVolumePreferencesScreen
import com.stratum.core.ui.trainingsetup.SchedulePreferencesScreen
import com.stratum.core.ui.trainingsetup.SpecializationPreferencesScreen
import com.stratum.feature.program.ActiveSessionDiscardReason
import com.stratum.feature.program.CreateProgramGenerateState
import com.stratum.feature.program.CreateProgramStep
import com.stratum.feature.program.CreateProgramUiState
import com.stratum.feature.program.R
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun CreateProgramScreen(
    state: CreateProgramUiState,
    callbacks: CreateProgramScreenCallbacks,
    modifier: Modifier = Modifier,
    userMessage: String? = null,
    sheetUndoEpoch: Int = 0,
) {
    val isEditor = state.step == CreateProgramStep.Editor
    val topBarTitle =
        when {
            isEditor && state.programName.isNotBlank() -> state.programName
            isEditor -> stringResource(R.string.create_program_default_name)
            else -> stringResource(R.string.create_program_title)
        }
    var showProgramOptionsSheet by remember { mutableStateOf(false) }
    var programOptionsMode by remember { mutableStateOf(CreateProgramOptionsMode.Menu) }
    var showReorderWorkouts by remember { mutableStateOf(false) }
    var reorderItems by remember { mutableStateOf<List<StratumReorderItem>>(emptyList()) }
    val sheetUndoHost =
        rememberProgramSheetUndoHost(
            userMessage = userMessage,
            sheetUndoEpoch = sheetUndoEpoch,
            step = state.step,
            onUndo = callbacks.onUndoSheetLift,
            onDismiss = callbacks.onDismissSheetUndo,
        )

    if (showReorderWorkouts) {
        BackHandler { showReorderWorkouts = false }
        StratumReorderListScreen(
            title = stringResource(CoreUiR.string.reorder_workouts_title),
            items = reorderItems,
            onReorder = { from, to ->
                if (from !in reorderItems.indices || to !in reorderItems.indices) return@StratumReorderListScreen
                val mutable = reorderItems.toMutableList()
                val moved = mutable.removeAt(from)
                mutable.add(to, moved)
                reorderItems = mutable
            },
            onCancel = { showReorderWorkouts = false },
            onConfirm = {
                callbacks.onDaysReordered(reorderItems.map { item -> item.id })
                showReorderWorkouts = false
            },
            emptyMessage = stringResource(CoreUiR.string.reorder_workouts_empty),
            confirmEnabled = reorderItems.isNotEmpty(),
            modifier = modifier,
        )
        return
    }

    // Glass (§12.8, STR-158/STR-191): under the route's glass host the top
    // bar overlays and each step carries the top clearance itself — the
    // scrolling preference steps via in-scroll top padding, the static steps
    // via top padding, the editor via its fixed tab column (its embedded
    // list starts below the tabs, so no double clearance). The per-step
    // bottom bars and IME behavior are untouched; solid hosts keep the
    // in-flow geometry exactly.
    StratumDetailScaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHostState = sheetUndoHost,
        topBar = {
            StratumTopBar(
                titleContent = {
                    Text(
                        text = topBarTitle,
                        style = StratumThemeAccessor.typography.titleLarge,
                        color = StratumThemeAccessor.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    StratumTopBarBackButton(
                        onClick = callbacks.onBack,
                        contentDescription = stringResource(R.string.create_program_back),
                    )
                },
                actions =
                    if (isEditor) {
                        {
                            StratumIconButton(
                                onClick = {
                                    programOptionsMode = CreateProgramOptionsMode.Menu
                                    showProgramOptionsSheet = true
                                },
                                modifier = Modifier.padding(end = StratumSpacing.s8),
                            ) {
                                Icon(
                                    painter = painterResource(CoreUiR.drawable.ic_more_vert),
                                    contentDescription =
                                        stringResource(R.string.create_program_options_a11y),
                                    tint = StratumThemeAccessor.colors.textPrimary,
                                )
                            }
                        }
                    } else {
                        null
                    },
            )
        },
        bottomBar = {
            when (state.step) {
                CreateProgramStep.Specialization,
                CreateProgramStep.MinimumVolume,
                -> WizardBottomBar(onNext = callbacks.onNext)
                CreateProgramStep.Schedule ->
                    WizardBottomBar(
                        nextLabel = stringResource(R.string.create_program_generate_cta),
                        onNext = callbacks.onGenerate,
                    )
                CreateProgramStep.Editor ->
                    EditorBottomBar(
                        isSaving = state.isSaving,
                        canSave = state.canSave,
                        saveError = state.saveErrorMessage,
                        showSaveChanges = state.showSaveChangesAction,
                        showSaveToLibrary = state.showSaveToLibraryAction,
                        showActivate = state.showActivateAction,
                        onSaveToLibrary = callbacks.onSaveToLibrary,
                        onSaveChanges = callbacks.onSaveChanges,
                        onActivate = callbacks.onActivate,
                    )
                else -> Unit
            }
        },
    ) { chrome ->
        if (state.loadErrorMessage != null) {
            // Glass: the error text clears the overlay bar like every other
            // step. Solid hosts keep the in-flow geometry (chrome.top is 0).
            Box(
                modifier = Modifier.fillMaxSize().padding(top = chrome.top),
                contentAlignment = Alignment.Center,
            ) {
                StratumText(
                    text = state.loadErrorMessage,
                    muted = true,
                    modifier = Modifier.padding(StratumSpacing.s24),
                )
            }
        } else when (state.step) {
            CreateProgramStep.ChoosePath ->
                ChooseCreatePathContent(
                    onSelectGenerate = callbacks.onSelectGenerate,
                    onSelectScratch = callbacks.onSelectScratch,
                    topClearance = chrome.top,
                )
            CreateProgramStep.Specialization ->
                SpecializationPreferencesScreen(
                    draft = state.specializationDraft,
                    onMuscleToggled = callbacks.onMuscleToggled,
                    topClearance = chrome.top,
                )
            CreateProgramStep.MinimumVolume ->
                MinimumVolumePreferencesScreen(
                    draft = state.minimumVolumeDraft,
                    onMuscleToggled = callbacks.onMinimumVolumeMuscleToggled,
                    onSuggestedMuscleToggled = callbacks.onSuggestedMinimumVolumeToggled,
                    topClearance = chrome.top,
                )
            CreateProgramStep.Schedule ->
                SchedulePreferencesScreen(
                    draft = state.scheduleDraft,
                    onTrainingDayCountChanged = callbacks.onTrainingDayCountChanged,
                    onSessionPresetSelected = callbacks.onSessionPresetSelected,
                    onCustomSessionMinutesChange = callbacks.onCustomSessionMinutesChange,
                    onSixDayCycleLengthSelected = callbacks.onSixDayCycleLengthSelected,
                    onTrainingSlotToggled = callbacks.onTrainingSlotToggled,
                    topClearance = chrome.top,
                )
            CreateProgramStep.Generating ->
                GeneratingContent(
                    progress = state.generateProgress,
                    maxActiveIndex = state.generateMaxTimelineIndex,
                    error = state.generateErrorMessage,
                    isError = state.generateState == CreateProgramGenerateState.Error,
                    onRetry = callbacks.onGenerate,
                    topClearance = chrome.top,
                )
            CreateProgramStep.Editor ->
                ProgramSheet(
                    state = state,
                    callbacks = callbacks,
                    topClearance = chrome.top,
                )
        }
    }

    if (state.showDiscardConfirm) {
        StratumBottomSheet(
            onDismissRequest = callbacks.onDismissDiscard,
            title = stringResource(R.string.create_program_discard_title),
        ) {
            StratumText(
                text = stringResource(R.string.create_program_discard_message),
                muted = true,
            )
            StratumButton(
                text = stringResource(R.string.create_program_discard_confirm),
                onClick = callbacks.onConfirmDiscard,
                modifier = Modifier.fillMaxWidth(),
            )
            StratumOutlinedButton(
                text = stringResource(R.string.create_program_discard_cancel),
                onClick = callbacks.onDismissDiscard,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    state.pendingActiveSessionDiscard?.let { reason ->
        val bodyRes =
            when (reason) {
                ActiveSessionDiscardReason.RemoveSelectedDay ->
                    R.string.create_program_active_session_discard_body_remove_day
                ActiveSessionDiscardReason.SaveChanges,
                ActiveSessionDiscardReason.SaveToLibrary,
                -> R.string.create_program_active_session_discard_body_save
                ActiveSessionDiscardReason.Activate ->
                    R.string.create_program_active_session_discard_body_activate
            }
        StratumConfirmDialog(
            title = stringResource(R.string.create_program_active_session_discard_title),
            body = stringResource(bodyRes),
            confirmLabel = stringResource(R.string.create_program_active_session_discard_confirm),
            dismissLabel = stringResource(R.string.create_program_active_session_discard_cancel),
            onConfirm = callbacks.onConfirmActiveSessionDiscard,
            onDismiss = callbacks.onDismissActiveSessionDiscard,
            destructiveConfirm = true,
            confirmEnabled = !state.isSaving,
        )
    }

    if (showProgramOptionsSheet) {
        CreateProgramOptionsBottomSheet(
            mode = programOptionsMode,
            programName = state.programName,
            onModeChange = { programOptionsMode = it },
            onReorderWorkouts = {
                showProgramOptionsSheet = false
                reorderItems =
                    state.days.map { day ->
                        StratumReorderItem(id = day.id, name = day.name)
                    }
                showReorderWorkouts = reorderItems.isNotEmpty()
            },
            onReorderLifts = {
                showProgramOptionsSheet = false
                callbacks.onReorderLifts()
            },
            onRenameProgram = { name ->
                callbacks.onRenameProgram(name)
                showProgramOptionsSheet = false
                programOptionsMode = CreateProgramOptionsMode.Menu
            },
            onDismiss = {
                showProgramOptionsSheet = false
                programOptionsMode = CreateProgramOptionsMode.Menu
            },
        )
    }

    if (state.showRenameWorkout) {
        RenameSheet(
            title = stringResource(R.string.create_program_rename_workout_title),
            initial = state.selectedDay?.name.orEmpty(),
            onConfirm = callbacks.onRenameWorkout,
            onDismiss = callbacks.onDismissRenameWorkout,
        )
    }
}

@Composable
private fun rememberProgramSheetUndoHost(
    userMessage: String?,
    sheetUndoEpoch: Int,
    step: CreateProgramStep,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
): SnackbarHostState? {
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.create_program_sheet_undo)
    val visible = userMessage != null && step == CreateProgramStep.Editor
    LaunchedEffect(sheetUndoEpoch) {
        val message = userMessage ?: return@LaunchedEffect
        if (!visible) {
            onDismiss()
            return@LaunchedEffect
        }
        // Indefinite so the action stays tappable. SnackbarDuration has no 3s preset.
        val result =
            withTimeoutOrNull(SHEET_UNDO_TIMEOUT_MS) {
                snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Indefinite,
                )
            }
        // A newer epoch cancels this call. Cancellation must not clear that epoch's undo.
        ensureActive()
        when (result) {
            SnackbarResult.ActionPerformed -> onUndo()
            SnackbarResult.Dismissed -> onDismiss()
            null -> {
                val current = snackbarHostState.currentSnackbarData
                if (current?.visuals?.message == message) {
                    current.dismiss()
                }
                ensureActive()
                onDismiss()
            }
        }
    }
    return if (visible) snackbarHostState else null
}

private const val SHEET_UNDO_TIMEOUT_MS = 3_000L

@Composable
private fun ChooseCreatePathContent(
    onSelectGenerate: () -> Unit,
    onSelectScratch: () -> Unit,
    // Glass (§12.8, STR-191): static step — the cards clear the bar via top
    // padding. 0 keeps solid hosts pixel-identical.
    topClearance: Dp = 0.dp,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = StratumSpacing.s12)
                .padding(top = StratumSpacing.s12 + topClearance, bottom = StratumSpacing.s12),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
    ) {
        StratumText(
            text = stringResource(R.string.create_program_choose_path_title),
            style = StratumThemeAccessor.typography.titleLarge,
        )
        PathCard(
            title = stringResource(R.string.create_program_generate_title),
            badge = stringResource(R.string.create_program_generate_recommended),
            description = stringResource(R.string.create_program_generate_description),
            onClick = onSelectGenerate,
        )
        PathCard(
            title = stringResource(R.string.create_program_scratch_title),
            badge = null,
            description = stringResource(R.string.create_program_scratch_description),
            onClick = onSelectScratch,
        )
    }
}

@Composable
private fun PathCard(
    title: String,
    badge: String?,
    description: String,
    onClick: () -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val elevations = StratumThemeAccessor.elevations
    val borderColor =
        if (elevations.useHardShadow) colors.borderStrong else colors.borderSubtle
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .stratumCardHardShadow(shape = shapes.panel)
                .background(
                    color = colors.surfaceRaised,
                    shape = shapes.panel,
                )
                .border(
                    width = shapes.borderWidth,
                    color = borderColor,
                    shape = shapes.panel,
                )
                .clickable(onClick = onClick)
                .padding(StratumSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StratumText(text = title, style = StratumThemeAccessor.typography.titleLarge)
            if (badge != null) {
                StratumText(
                    text = badge,
                    style = StratumThemeAccessor.typography.sectionLabel,
                    muted = true,
                )
            }
        }
        StratumText(text = description, muted = true)
    }
}

@Composable
private fun GeneratingContent(
    progress: ProgramGenerationProgressUi?,
    maxActiveIndex: Int,
    error: String?,
    isError: Boolean,
    onRetry: () -> Unit,
    // Glass (§12.8, STR-191): static step — clears the bar via top padding.
    // 0 keeps solid hosts pixel-identical.
    topClearance: Dp = 0.dp,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = StratumSpacing.s24)
                .padding(top = StratumSpacing.s16 + topClearance, bottom = StratumSpacing.s16),
        verticalArrangement =
            if (isError) {
                Arrangement.Center
            } else {
                Arrangement.Top
            },
        horizontalAlignment = if (isError) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        if (isError) {
            StratumText(
                text = error ?: stringResource(R.string.create_program_generate_error),
                muted = true,
            )
            Spacer(Modifier.height(StratumSpacing.s16))
            StratumButton(text = stringResource(R.string.create_program_generate_cta), onClick = onRetry)
        } else {
            ProgramGenerationLoadingContent(
                progress = progress,
                maxActiveIndex = maxActiveIndex,
            )
        }
    }
}

internal fun editWorkoutCallbacks(callbacks: CreateProgramScreenCallbacks): EditWorkoutScreenCallbacks =
    EditWorkoutScreenCallbacks(
        onAddSet = callbacks.onAddSet,
        onRemoveSet = callbacks.onRemoveSet,
        onRemoveLift = callbacks.onRemoveLift,
        onLinkSupersetWithPrevious = callbacks.onLinkSupersetWithPrevious,
        onLinkSupersetWithNext = callbacks.onLinkSupersetWithNext,
        onRemoveFromSuperset = callbacks.onRemoveFromSuperset,
        onSetUseRestTimerBetweenLifts = callbacks.onSetUseRestTimerBetweenLifts,
        onAddLift = callbacks.onAddExercise,
        onSwapLiftClick = callbacks.onSwapLift,
        onReorderLiftsClick = callbacks.onReorderLifts,
        onSetRepBand = callbacks.onSetRepBand,
        onSetRirGoal = callbacks.onSetRirGoal,
        onSetRestSeconds = callbacks.onSetRestSeconds,
        onSetRestSecondsForAllSetsOnLift = callbacks.onSetRestSecondsForAllSetsOnLift,
    )

@Composable
private fun WizardBottomBar(
    onNext: () -> Unit,
    nextLabel: String = stringResource(R.string.create_program_next),
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(StratumThemeAccessor.colors.surfaceRaised)
                .stratumDockTopRule()
                .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s12)
                .navigationBarsPadding(),
    ) {
        StratumButton(
            text = nextLabel,
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EditorBottomBar(
    isSaving: Boolean,
    canSave: Boolean,
    saveError: String?,
    showSaveChanges: Boolean,
    showSaveToLibrary: Boolean,
    showActivate: Boolean,
    onSaveToLibrary: () -> Unit,
    onSaveChanges: () -> Unit,
    onActivate: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(StratumThemeAccessor.colors.surfaceRaised)
                .stratumDockTopRule()
                .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s12)
                .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
    ) {
        if (saveError != null) {
            StratumText(text = saveError, muted = true)
        }
        if (showSaveChanges) {
            StratumButton(
                text =
                    if (isSaving) {
                        stringResource(R.string.create_program_saving)
                    } else {
                        stringResource(R.string.create_program_save_changes)
                    },
                onClick = onSaveChanges,
                enabled = canSave && !isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (showSaveToLibrary) {
            StratumOutlinedButton(
                text =
                    if (isSaving) {
                        stringResource(R.string.create_program_saving)
                    } else {
                        stringResource(R.string.create_program_save_to_library)
                    },
                onClick = onSaveToLibrary,
                enabled = canSave && !isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (showActivate) {
            StratumButton(
                text =
                    if (isSaving) {
                        stringResource(R.string.create_program_saving)
                    } else {
                        stringResource(R.string.create_program_activate)
                    },
                onClick = onActivate,
                enabled = canSave && !isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RenameSheet(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    StratumBottomSheet(onDismissRequest = onDismiss, title = title) {
        StratumTextField(
            value = value,
            onValueChange = { value = it },
            label = title,
            singleLine = true,
        )
        StratumButton(
            text = stringResource(R.string.create_program_rename_confirm),
            onClick = { onConfirm(value) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}


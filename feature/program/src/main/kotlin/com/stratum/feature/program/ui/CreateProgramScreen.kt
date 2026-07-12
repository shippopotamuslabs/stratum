package com.stratum.feature.program.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.ProgramGenerationLoadingContent
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumScopedMuscleVolumeRow
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextField
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.progress.ProgramGenerationProgressUi
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.trainingsetup.MinimumVolumePreferencesScreen
import com.stratum.core.ui.trainingsetup.SchedulePreferencesScreen
import com.stratum.core.ui.trainingsetup.SpecializationPreferencesScreen
import com.stratum.feature.program.CreateProgramDayUi
import com.stratum.feature.program.CreateProgramGenerateState
import com.stratum.feature.program.CreateProgramStep
import com.stratum.feature.program.CreateProgramUiState
import com.stratum.feature.program.MAX_CREATE_PROGRAM_DAYS
import com.stratum.feature.program.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun CreateProgramScreen(
    state: CreateProgramUiState,
    callbacks: CreateProgramScreenCallbacks,
    modifier: Modifier = Modifier,
) {
    val isEditor = state.step == CreateProgramStep.Editor
    val topBarTitle =
        when {
            isEditor && state.programName.isNotBlank() -> state.programName
            isEditor -> stringResource(R.string.create_program_default_name)
            else -> stringResource(R.string.create_program_title)
        }
    StratumScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            StratumTopBar(
                titleContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = topBarTitle,
                            style = StratumThemeAccessor.typography.titleLarge,
                            color = StratumThemeAccessor.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (isEditor) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription =
                                    stringResource(R.string.create_program_edit_name),
                                tint = StratumThemeAccessor.colors.textMuted,
                                modifier =
                                    Modifier
                                        .size(22.dp)
                                        .clickable(
                                            onClick = callbacks.onShowRenameProgram,
                                            role = Role.Button,
                                        ),
                            )
                        }
                    }
                },
                navigationIcon = {
                    StratumTopBarBackButton(
                        onClick = callbacks.onBack,
                        contentDescription = stringResource(R.string.create_program_back),
                    )
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
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            if (state.loadErrorMessage != null) {
                StratumText(
                    text = state.loadErrorMessage,
                    muted = true,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            } else when (state.step) {
                CreateProgramStep.ChoosePath ->
                    ChooseCreatePathContent(
                        onSelectGenerate = callbacks.onSelectGenerate,
                        onSelectScratch = callbacks.onSelectScratch,
                    )
                CreateProgramStep.Specialization ->
                    SpecializationPreferencesScreen(
                        draft = state.specializationDraft,
                        onMuscleToggled = callbacks.onMuscleToggled,
                    )
                CreateProgramStep.MinimumVolume ->
                    MinimumVolumePreferencesScreen(
                        draft = state.minimumVolumeDraft,
                        onMuscleToggled = callbacks.onMinimumVolumeMuscleToggled,
                        onSuggestedMuscleToggled = callbacks.onSuggestedMinimumVolumeToggled,
                    )
                CreateProgramStep.Schedule ->
                    SchedulePreferencesScreen(
                        draft = state.scheduleDraft,
                        onWeekdayToggled = callbacks.onWeekdayToggled,
                        onSessionPresetSelected = callbacks.onSessionPresetSelected,
                        onCustomSessionMinutesChange = callbacks.onCustomSessionMinutesChange,
                    )
                CreateProgramStep.Generating ->
                    GeneratingContent(
                        progress = state.generateProgress,
                        maxActiveIndex = state.generateMaxTimelineIndex,
                        error = state.generateErrorMessage,
                        isError = state.generateState == CreateProgramGenerateState.Error,
                        onRetry = callbacks.onGenerate,
                    )
                CreateProgramStep.Editor ->
                    CreateProgramEditorContent(
                        state = state,
                        callbacks = callbacks,
                    )
            }
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

    if (state.showRenameProgram) {
        RenameSheet(
            title = stringResource(R.string.create_program_rename_program_title),
            initial = state.programName,
            onConfirm = callbacks.onRenameProgram,
            onDismiss = callbacks.onDismissRenameProgram,
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
private fun ChooseCreatePathContent(
    onSelectGenerate: () -> Unit,
    onSelectScratch: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color = StratumThemeAccessor.colors.surfaceRaised,
                    shape = StratumThemeAccessor.shapes.panel,
                )
                .border(
                    width = StratumThemeAccessor.shapes.borderWidth,
                    color = StratumThemeAccessor.colors.borderSubtle,
                    shape = StratumThemeAccessor.shapes.panel,
                )
                .clickable(onClick = onClick)
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
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
            Spacer(Modifier.height(16.dp))
            StratumButton(text = stringResource(R.string.create_program_generate_cta), onClick = onRetry)
        } else {
            ProgramGenerationLoadingContent(
                progress = progress,
                maxActiveIndex = maxActiveIndex,
            )
        }
    }
}

@Composable
private fun CreateProgramEditorContent(
    state: CreateProgramUiState,
    callbacks: CreateProgramScreenCallbacks,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        DayTabsRow(
            days = state.days,
            selectedDayId = state.selectedDayId,
            canAddDay = state.canAddDay,
            onSelectDay = callbacks.onSelectDay,
            onAddDay = callbacks.onAddDay,
            onReorderDays = callbacks.onReorderDays,
        )
        DayActionButtonsRow(
            onRemove = callbacks.onRemoveDay,
            onRenameWorkout = callbacks.onShowRenameWorkout,
        )
        CreateProgramVolumeRow(state = state)
        val selected = state.selectedDay
        when {
            selected == null ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    StratumText(text = stringResource(R.string.create_program_add_day), muted = true)
                }
            selected.isRest || selected.editState == null || selected.editState.lifts.isEmpty() ->
                RestDayBody(onAddExercise = callbacks.onAddExercise)
            else ->
                EmbeddedWorkoutEditor(
                    editState = selected.editState,
                    onUpdate = callbacks.onUpdateSelectedWorkout,
                    onAddExercise = callbacks.onAddExercise,
                    onReorderLifts = callbacks.onReorderLifts,
                    onSwapLift = callbacks.onSwapLift,
                    modifier = Modifier.weight(1f),
                )
        }
    }
}

@Composable
private fun CreateProgramVolumeRow(state: CreateProgramUiState) {
    val selectedEdit = state.selectedDay?.editState
    val metricsEdit =
        selectedEdit
            ?: state.days.firstNotNullOfOrNull { day -> day.editState }
            ?: return
    val workoutCards =
        if (selectedEdit != null) {
            selectedEdit.upperVolumeByMuscle + selectedEdit.lowerVolumeByMuscle
        } else {
            emptyList()
        }
    val programCards = metricsEdit.programUpperVolumeByMuscle + metricsEdit.programLowerVolumeByMuscle
    if (workoutCards.isEmpty() && programCards.isEmpty()) return
    StratumScopedMuscleVolumeRow(
        workoutCards = workoutCards,
        programCards = programCards,
        title = stringResource(R.string.edit_workout_volume_title),
        scopeLabels =
            listOf(
                stringResource(R.string.edit_workout_volume_scope_workout),
                stringResource(R.string.edit_workout_volume_scope_program),
            ),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

private const val DAY_TAB_REORDER_THRESHOLD_DP = 72f
private val DayTabBarHeight = 48.dp
private val DayTabIndicatorHeight = 2.dp
/** Viewport edge band that triggers auto-scroll while reordering. */
private val DayTabAutoScrollEdge = 56.dp
/** Max scroll per frame (~60fps) when the finger is fully in the edge band. */
private val DayTabAutoScrollMaxSpeed = 14.dp
private const val DAY_TAB_AUTO_SCROLL_FRAME_MS = 16L

@Composable
private fun DayTabsRow(
    days: List<CreateProgramDayUi>,
    selectedDayId: String?,
    canAddDay: Boolean,
    onSelectDay: (String) -> Unit,
    onAddDay: () -> Unit,
    onReorderDays: (fromIndex: Int, toIndex: Int) -> Unit,
) {
    val scrollState = rememberScrollState()
    var draggedDayId by remember { mutableStateOf<String?>(null) }
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var tabWidthsPx by remember { mutableStateOf(emptyMap<String, Float>()) }
    var viewportWidthPx by remember { mutableFloatStateOf(0f) }
    var pointerXInViewport by remember { mutableFloatStateOf(Float.NaN) }
    var viewportCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val density = LocalDensity.current
    val densityScale = density.density
    val edgeZonePx = with(density) { DayTabAutoScrollEdge.toPx() }
    val maxScrollSpeedPx = with(density) { DayTabAutoScrollMaxSpeed.toPx() }
    val colors = StratumThemeAccessor.colors
    val borderWidth = StratumThemeAccessor.shapes.borderWidth
    val latestDays by rememberUpdatedState(days)
    val latestOnReorderDays by rememberUpdatedState(onReorderDays)

    fun applyDragDelta(delta: Float) {
        if (draggedIndex < 0 || draggedDayId == null) return
        dragOffset += delta
        val currentWidth =
            tabWidthsPx[draggedDayId]
                ?: (DAY_TAB_REORDER_THRESHOLD_DP * densityScale)
        val thresholdPx = (currentWidth * 0.55f).coerceAtLeast(40f * densityScale)
        val dayCount = latestDays.size
        while (dragOffset > thresholdPx && draggedIndex < dayCount - 1) {
            latestOnReorderDays(draggedIndex, draggedIndex + 1)
            draggedIndex += 1
            dragOffset -= thresholdPx
        }
        while (dragOffset < -thresholdPx && draggedIndex > 0) {
            latestOnReorderDays(draggedIndex, draggedIndex - 1)
            draggedIndex -= 1
            dragOffset += thresholdPx
        }
    }

    // Edge auto-scroll while a day tab is being reordered.
    val isDragging = draggedDayId != null
    LaunchedEffect(isDragging) {
        if (!isDragging) return@LaunchedEffect
        while (isActive) {
            val x = pointerXInViewport
            val width = viewportWidthPx
            if (!x.isNaN() && width > 0f && edgeZonePx > 0f) {
                val scrollDelta =
                    when {
                        x < edgeZonePx -> {
                            val t = ((edgeZonePx - x) / edgeZonePx).coerceIn(0f, 1f)
                            -maxScrollSpeedPx * t
                        }
                        x > width - edgeZonePx -> {
                            val t = ((x - (width - edgeZonePx)) / edgeZonePx).coerceIn(0f, 1f)
                            maxScrollSpeedPx * t
                        }
                        else -> 0f
                    }
                if (scrollDelta != 0f) {
                    val before = scrollState.value
                    scrollState.scrollBy(scrollDelta)
                    val actual = (scrollState.value - before).toFloat()
                    if (actual != 0f) {
                        // Keep the dragged chip under the finger as content scrolls.
                        applyDragDelta(actual)
                    }
                }
            }
            delay(DAY_TAB_AUTO_SCROLL_FRAME_MS)
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(DayTabBarHeight)
                .onSizeChanged { size -> viewportWidthPx = size.width.toFloat() }
                .onGloballyPositioned { coords -> viewportCoordinates = coords }
                .drawBehind {
                    val y = size.height - borderWidth.toPx() / 2f
                    drawLine(
                        color = colors.borderSubtle,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = borderWidth.toPx(),
                    )
                },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            days.forEachIndexed { index, day ->
                val selected = day.id == selectedDayId
                val tabDragging = day.id == draggedDayId
                DayTab(
                    name = day.name,
                    selected = selected,
                    isDragging = tabDragging,
                    dragOffsetX = if (tabDragging) dragOffset else 0f,
                    viewportCoordinates = { viewportCoordinates },
                    onSelect = { onSelectDay(day.id) },
                    onSizeChanged = { widthPx ->
                        tabWidthsPx = tabWidthsPx + (day.id to widthPx)
                    },
                    onDragStart = {
                        draggedDayId = day.id
                        draggedIndex = index
                        dragOffset = 0f
                    },
                    onDragEnd = {
                        draggedDayId = null
                        draggedIndex = -1
                        dragOffset = 0f
                        pointerXInViewport = Float.NaN
                    },
                    onHorizontalDrag = { dragAmount, pointerX ->
                        pointerXInViewport = pointerX
                        applyDragDelta(dragAmount)
                    },
                )
            }
            // Hide once the Mon–Sun week is full (multi-week chains deferred).
            if (canAddDay) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .clickable(onClick = onAddDay)
                            .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    StratumText(
                        text = stringResource(R.string.create_program_add_day) + " +",
                        style = StratumThemeAccessor.typography.bodyLarge,
                        muted = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayTab(
    name: String,
    selected: Boolean,
    isDragging: Boolean,
    dragOffsetX: Float,
    viewportCoordinates: () -> LayoutCoordinates?,
    onSelect: () -> Unit,
    onSizeChanged: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onHorizontalDrag: (dragAmount: Float, pointerXInViewport: Float) -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val haptic = LocalHapticFeedback.current
    val dragDescription = stringResource(R.string.create_program_drag_day)
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)
    val latestOnHorizontalDrag by rememberUpdatedState(onHorizontalDrag)
    val latestViewportCoordinates by rememberUpdatedState(viewportCoordinates)
    var handleCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Column(
        modifier =
            Modifier
                .zIndex(if (isDragging) 1f else 0f)
                .graphicsLayer {
                    translationX = dragOffsetX
                    alpha = if (isDragging) 0.92f else 1f
                }
                .fillMaxHeight()
                .defaultMinSize(minWidth = 72.dp)
                .onSizeChanged { size -> onSizeChanged(size.width.toFloat()) }
                .semantics {
                    role = Role.Tab
                    this.selected = selected
                }
                .clickable(onClick = onSelect)
                .drawBehind {
                    if (selected) {
                        val stroke = DayTabIndicatorHeight.toPx()
                        val y = size.height - stroke / 2f
                        drawLine(
                            color = colors.accent,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = stroke,
                        )
                    }
                }
                .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StratumText(
                text = name,
                style = StratumThemeAccessor.typography.bodyLarge,
                muted = !selected,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Long-press (~system 500ms) arms reorder; before that, swipes scroll the day strip.
            // Near the strip edges while dragging, DayTabsRow auto-scrolls so long moves work.
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .onGloballyPositioned { coords -> handleCoordinates = coords }
                        .pointerInput(Unit) {
                            fun pointerXInViewport(localPosition: Offset): Float {
                                val handle = handleCoordinates?.takeIf { it.isAttached }
                                    ?: return Float.NaN
                                val viewport = latestViewportCoordinates()?.takeIf { it.isAttached }
                                    ?: return Float.NaN
                                val root = handle.localToRoot(localPosition)
                                val origin = viewport.localToRoot(Offset.Zero)
                                return root.x - origin.x
                            }
                            detectDragGesturesAfterLongPress(
                                onDragStart = { startOffset ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    latestOnDragStart()
                                    // Seed edge-scroll position before the first move event.
                                    latestOnHorizontalDrag(0f, pointerXInViewport(startOffset))
                                },
                                onDragEnd = { latestOnDragEnd() },
                                onDragCancel = { latestOnDragEnd() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    latestOnHorizontalDrag(
                                        dragAmount.x,
                                        pointerXInViewport(change.position),
                                    )
                                },
                            )
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_drag_handle),
                    contentDescription = dragDescription,
                    tint = if (selected) colors.textPrimary else colors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun DayActionButtonsRow(
    onRemove: () -> Unit,
    onRenameWorkout: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StratumOutlinedButton(
            text = stringResource(R.string.create_program_remove_day),
            onClick = onRemove,
            modifier = Modifier.weight(1f),
        )
        StratumOutlinedButton(
            text = stringResource(R.string.create_program_rename_workout),
            onClick = onRenameWorkout,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RestDayBody(onAddExercise: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StratumText(
            text = stringResource(R.string.create_program_rest_day),
            style = StratumThemeAccessor.typography.titleLarge,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(
                        width = StratumThemeAccessor.shapes.borderWidth,
                        color = StratumThemeAccessor.colors.borderSubtle,
                        shape = StratumThemeAccessor.shapes.panel,
                    )
                    .padding(20.dp),
        ) {
            StratumText(
                text = stringResource(R.string.create_program_rest_day_hint),
                muted = true,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StratumText(
                text = stringResource(R.string.create_program_zero_lifts),
                style = StratumThemeAccessor.typography.titleLarge,
            )
            StratumText(
                text = stringResource(R.string.create_program_add_lifts),
                style =
                    StratumThemeAccessor.typography.bodyLarge.copy(
                        color = StratumThemeAccessor.colors.accent,
                    ),
                modifier = Modifier.clickable(onClick = onAddExercise),
            )
        }
    }
}

@Composable
private fun EmbeddedWorkoutEditor(
    editState: EditWorkoutUiState.Loaded,
    onUpdate: ((EditWorkoutUiState.Loaded) -> EditWorkoutUiState.Loaded) -> Unit,
    onAddExercise: () -> Unit,
    onReorderLifts: () -> Unit,
    onSwapLift: (workoutId: String, workoutLiftId: String?, liftId: String?, liftName: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    EditWorkoutEmbeddedContent(
        state = editState,
        callbacks =
            EditWorkoutScreenCallbacks(
                onAddSet = { workoutLiftId ->
                    onUpdate { loaded ->
                        loaded.copy(
                            lifts =
                                loaded.lifts.map { lift ->
                                    if (lift.workoutLiftId != workoutLiftId) {
                                        lift
                                    } else {
                                        lift.copy(
                                            sets = renumberSetsLocal(lift.sets + defaultSet(lift.sets.size + 1)),
                                        )
                                    }
                                },
                            isDirty = true,
                        )
                    }
                },
                onRemoveSet = { setId ->
                    onUpdate { loaded ->
                        loaded.copy(
                            lifts =
                                loaded.lifts.map { lift ->
                                    val next = lift.sets.filterNot { set -> set.setId == setId }
                                    if (next.size == lift.sets.size) {
                                        lift
                                    } else {
                                        lift.copy(sets = renumberSetsLocal(next))
                                    }
                                },
                            isDirty = true,
                        )
                    }
                },
                onRemoveLift = { workoutLiftId ->
                    onUpdate { loaded ->
                        loaded.copy(
                            lifts =
                                loaded.lifts
                                    .filterNot { lift -> lift.workoutLiftId == workoutLiftId }
                                    .mapIndexed { index, lift -> lift.copy(position = index) },
                            isDirty = true,
                        )
                    }
                },
                onAddLift = onAddExercise,
                onSwapLiftClick = onSwapLift,
                onReorderLiftsClick = onReorderLifts,
                onSetRepMin = { setId, value ->
                    onUpdate { loaded -> loaded.updateSet(setId) { set -> set.copy(repMin = value) } }
                },
                onSetRepMax = { setId, value ->
                    onUpdate { loaded -> loaded.updateSet(setId) { set -> set.copy(repMax = value) } }
                },
                onSetRirGoal = { setId, value ->
                    onUpdate { loaded -> loaded.updateSet(setId) { set -> set.copy(rirGoal = value) } }
                },
                onSetRestSeconds = { setId, value ->
                    onUpdate { loaded -> loaded.updateSet(setId) { set -> set.copy(restSeconds = value) } }
                },
                onSetRestSecondsForAllSetsOnLift = { workoutLiftId, value ->
                    onUpdate { loaded ->
                        loaded.copy(
                            lifts =
                                loaded.lifts.map { lift ->
                                    if (lift.workoutLiftId != workoutLiftId) {
                                        lift
                                    } else {
                                        lift.copy(
                                            sets = lift.sets.map { set -> set.copy(restSeconds = value) },
                                        )
                                    }
                                },
                            isDirty = true,
                        )
                    }
                },
            ),
        modifier = modifier,
    )
}

private fun EditWorkoutUiState.Loaded.updateSet(
    setId: String,
    transform: (EditSetState) -> EditSetState,
): EditWorkoutUiState.Loaded =
    copy(
        lifts =
            lifts.map { lift ->
                lift.copy(
                    sets =
                        lift.sets.map { set ->
                            if (set.setId == setId) transform(set) else set
                        },
                )
            },
        isDirty = true,
    )

private fun renumberSetsLocal(sets: List<EditSetState>): List<EditSetState> =
    sets.mapIndexed { index, set -> set.copy(setNumber = index + 1) }

private fun defaultSet(setNumber: Int): EditSetState =
    EditSetState(
        setId = java.util.UUID.randomUUID().toString(),
        setNumber = setNumber,
        repMin = if (setNumber == 1) DEFAULT_REP_MIN else 0,
        repMax = if (setNumber == 1) DEFAULT_REP_MAX else 0,
        restSeconds = DEFAULT_REST_SECONDS,
        rirGoal = DEFAULT_RIR_GOAL,
        isTopSet = setNumber == 1,
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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

@Preview(name = "Day tabs light", showBackground = true)
@Composable
internal fun DayTabsRowPreviewLight() {
    StratumTheme(darkTheme = false) {
        DayTabsRowPreviewContent()
    }
}

@Preview(name = "Day tabs dark", showBackground = true, backgroundColor = 0xFF121212)
@Composable
internal fun DayTabsRowPreviewDark() {
    StratumTheme(darkTheme = true) {
        DayTabsRowPreviewContent()
    }
}

@Composable
private fun DayTabsRowPreviewContent() {
    var days by remember {
        mutableStateOf(
            listOf(
                CreateProgramDayUi(id = "a", name = "Workout A", position = 0, isRest = false, editState = null),
                CreateProgramDayUi(id = "r1", name = "Rest", position = 1, isRest = true, editState = null),
                CreateProgramDayUi(id = "b", name = "Workout B", position = 2, isRest = false, editState = null),
                CreateProgramDayUi(id = "r2", name = "Rest", position = 3, isRest = true, editState = null),
            ),
        )
    }
    var selectedId by remember { mutableStateOf("a") }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(StratumThemeAccessor.colors.surfaceBase)
                .padding(vertical = 8.dp),
    ) {
        DayTabsRow(
            days = days,
            selectedDayId = selectedId,
            canAddDay = days.size < MAX_CREATE_PROGRAM_DAYS,
            onSelectDay = { selectedId = it },
            onAddDay = {
                if (days.size >= MAX_CREATE_PROGRAM_DAYS) return@DayTabsRow
                val next = days.size + 1
                days = days +
                    CreateProgramDayUi(
                        id = "new-$next",
                        name = "Workout $next",
                        position = days.size,
                        isRest = false,
                        editState = null,
                    )
            },
            onReorderDays = reorder@{ from, to ->
                if (from !in days.indices || to !in days.indices) return@reorder
                val mutable = days.toMutableList()
                val moved = mutable.removeAt(from)
                mutable.add(to, moved)
                days = mutable.mapIndexed { index, day -> day.copy(position = index) }
            },
        )
    }
}

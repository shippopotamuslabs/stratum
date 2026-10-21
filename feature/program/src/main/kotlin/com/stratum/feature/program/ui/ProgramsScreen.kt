// STR-142 allowlist: active-workout-bar clearance inset (96.dp) and dense 2.dp stack pinned.
@file:Suppress("ForbiddenRawSpacingLiteralInUi")
package com.stratum.feature.program.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumCheckbox
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumMuscleVolumeRow
import com.stratum.core.ui.component.StratumVolumeBodyTier
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumTabScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.rememberGlassChromePadding
import com.stratum.core.ui.theme.rememberGlassBottomDockPadding
import com.stratum.core.ui.theme.stratumDockSurface
import com.stratum.feature.program.R
import kotlin.time.Duration.Companion.milliseconds

/**
 * Read-only Programs overview. Renders volume summary, weekly schedule with rest days, and workout
 * cards with a header row (name, weekday, skip checkbox) and a flat lift list.
 */
@Composable
internal fun ProgramsScreen(
    state: ProgramsUiState,
    callbacks: ProgramsScreenCallbacks,
    onSwapLiftClick: (String, WorkoutLiftLineState) -> Unit,
    modifier: Modifier = Modifier,
    activeWorkoutBar: MinimizedWorkoutState? = null,
    /** Last expanded workout card id from DataStore; null means all cards collapsed. */
    expandedWorkoutId: String? = null,
    onCancelWorkoutClick: () -> Unit = {},
    onExpandWorkoutClick: (String) -> Unit = {},
    isPreviewMode: Boolean = false,
    showCheckInBanner: Boolean = false,
    onCheckInBannerClick: () -> Unit = {},
    onCheckInSkipClick: () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
) {
    val programsScrollState = rememberScrollState()
    val chrome = rememberGlassChromePadding()

    var showOptionsSheet by remember { mutableStateOf(false) }
    var pendingUnskipWorkoutId by remember { mutableStateOf<String?>(null) }
    var pendingUnskipWorkoutName by remember { mutableStateOf<String?>(null) }
    var pendingStartAction by remember { mutableStateOf<ProgramsStartAction?>(null) }

    StratumTabScaffold(
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        overlayTopBar = chrome.active,
        topBar = {
            StratumTopBar(
                title = stringResource(R.string.programs_screen_title),
                navigationIcon = if (isPreviewMode) null else {
                    {
                        StratumIconButton(onClick = callbacks.onCalendarClick) {
                            Icon(
                                painter = painterResource(CoreUiR.drawable.ic_calendar),
                                contentDescription = stringResource(R.string.programs_calendar_content_description),
                                tint = StratumThemeAccessor.colors.textPrimary,
                            )
                        }
                    }
                },
                actions = if (isPreviewMode) null else {
                    {
                        StratumIconButton(onClick = { showOptionsSheet = true }) {
                            Icon(
                                painter = painterResource(CoreUiR.drawable.ic_more_vert),
                                contentDescription = stringResource(R.string.programs_menu_content_description),
                                tint = StratumThemeAccessor.colors.textPrimary,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (isPreviewMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StratumThemeAccessor.colors.surfaceRaised)
                        .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s12)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
                ) {
                    StratumButton(
                        text = stringResource(R.string.programs_keep_program),
                        onClick = callbacks.onKeepProgramClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    StratumOutlinedButton(
                        text = stringResource(R.string.programs_discard_program),
                        onClick = callbacks.onDiscardProgramClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        horizontalPadding = 0.dp,
        bottomOverlay = {
            if (activeWorkoutBar != null) {
                ActiveWorkoutBar(
                    activeWorkoutBar = activeWorkoutBar,
                    onExpandWorkoutClick = onExpandWorkoutClick,
                    onCancelWorkoutClick = onCancelWorkoutClick,
                )
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(programsScrollState)
                    .padding(horizontal = StratumSpacing.s16)
                    .padding(
                        top = StratumSpacing.s16 + chrome.top,
                        bottom =
                            StratumSpacing.s16 +
                                (if (activeWorkoutBar != null) 96.dp else 0.dp) +
                                chrome.bottom,
                    ),
                verticalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
            ) {
                if (!isPreviewMode) {
                    if (showCheckInBanner) {
                        CheckInBanner(
                            onTap = onCheckInBannerClick,
                            onSkip = onCheckInSkipClick,
                        )
                    }
                    val hasActiveWorkout = activeWorkoutBar != null
                    // Stacked CTA pair: 8dp within the pair (Compose Gospel), not the
                    // 16dp section rhythm of the surrounding column.
                    Column(verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8)) {
                        StratumButton(
                            text = stringResource(R.string.programs_start_next_workout),
                            onClick = {
                                if (hasActiveWorkout) {
                                    pendingStartAction = ProgramsStartAction.StartNextWorkout
                                } else {
                                    callbacks.onStartNextWorkoutClick()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        StratumOutlinedButton(
                            text = stringResource(R.string.programs_start_empty_workout),
                            onClick = {
                                if (hasActiveWorkout) {
                                    pendingStartAction = ProgramsStartAction.StartEmptyWorkout
                                } else {
                                    callbacks.onStartEmptyWorkoutClick()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                when (state) {
                    is ProgramsUiState.Empty ->
                        StratumText(text = stringResource(R.string.programs_empty), muted = true)
                    is ProgramsUiState.Loaded -> LoadedProgramContent(
                        state = state,
                        callbacks = callbacks,
                        onSwapLiftClick = onSwapLiftClick,
                        isPreviewMode = isPreviewMode,
                        hasActiveWorkout = activeWorkoutBar != null,
                        expandedWorkoutId = expandedWorkoutId,
                        onPendingStart = { action -> pendingStartAction = action },
                        onPendingUnskip = { workoutId, workoutName ->
                            pendingUnskipWorkoutId = workoutId
                            pendingUnskipWorkoutName = workoutName
                        },
                    )
                }
            }
        }

        pendingUnskipWorkoutId?.let { workoutId ->
            UnskipWorkoutDialog(
                onDismiss = {
                    pendingUnskipWorkoutId = null
                    pendingUnskipWorkoutName = null
                },
                onConfirm = {
                    callbacks.onSkipWorkoutCheckedChange(workoutId, false)
                    pendingUnskipWorkoutId = null
                    pendingUnskipWorkoutName = null
                },
            )
        }

        pendingStartAction?.let { action ->
            StartSessionDiscardDialog(
                onDismiss = { pendingStartAction = null },
                onConfirm = {
                    callbacks.onConfirmDiscardAndStart(action)
                    pendingStartAction = null
                },
            )
        }

        if (showOptionsSheet) {
            val editProgramId = (state as? ProgramsUiState.Loaded)?.program?.programId
            ProgramsOptionsBottomSheet(
                onNewProgram = {
                    showOptionsSheet = false
                    callbacks.onCreateProgramClick()
                },
                onProgramLibrary = {
                    showOptionsSheet = false
                    callbacks.onProgramLibraryClick()
                },
                onEditProgram =
                    editProgramId?.let { programId ->
                        {
                            showOptionsSheet = false
                            callbacks.onEditProgramClick(programId)
                        }
                    },
                onReorderWorkouts =
                    editProgramId?.let { programId ->
                        {
                            showOptionsSheet = false
                            callbacks.onReorderWorkoutsClick(programId)
                        }
                    },
                onDismiss = { showOptionsSheet = false },
            )
        }
    }
}

@Composable
private fun ActiveWorkoutBar(
    activeWorkoutBar: MinimizedWorkoutState,
    onExpandWorkoutClick: (String) -> Unit,
    onCancelWorkoutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCancelConfirm by remember { mutableStateOf(false) }
    var elapsedTimeStr by remember(activeWorkoutBar.startedAtEpochMilli) { mutableStateOf("00:00") }

    LaunchedEffect(activeWorkoutBar.startedAtEpochMilli) {
        while (true) {
            val now = System.currentTimeMillis()
            val diffMs = now - activeWorkoutBar.startedAtEpochMilli
            val diffSec = (diffMs / 1000).coerceAtLeast(0)
            val hrs = diffSec / 3600
            val mins = (diffSec % 3600) / 60
            val secs = diffSec % 60
            elapsedTimeStr = if (hrs > 0) {
                String.format(java.util.Locale.US, "%d:%02d:%02d", hrs, mins, secs)
            } else {
                String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
            }
            kotlinx.coroutines.delay(1000L.milliseconds)
        }
    }

    val panelShape = StratumThemeAccessor.shapes.panel
    val borderWidth = StratumThemeAccessor.shapes.borderWidth
    val borderSubtleColor = StratumThemeAccessor.colors.borderSubtle
    val drawerShape = remember(panelShape) {
        if (panelShape is RoundedCornerShape) {
            RoundedCornerShape(
                topStart = panelShape.topStart,
                topEnd = panelShape.topEnd,
                bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp),
                bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp)
            )
        } else {
            RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = 0.dp,
                bottomEnd = 0.dp
            )
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            // Under glass the drawer docks over the nav bar (STR-156): the
            // canonical dock offset lifts it clear of the nav glass; the 8dp
            // drop shadow below is deliberate and kept in both modes.
            .padding(bottom = rememberGlassBottomDockPadding())
            .shadow(
                elevation = 8.dp,
                shape = drawerShape,
                clip = false
            )
            .stratumDockSurface(
                shape = drawerShape,
                solidColor = StratumThemeAccessor.colors.surfaceRaised,
            )
            .drawBehind {
                val strokeWidth = borderWidth.toPx()
                val halfStroke = strokeWidth / 2f
                val topStartPx = drawerShape.topStart.toPx(size, this)
                val topRightPx = drawerShape.topEnd.toPx(size, this)

                val path = Path().apply {
                    moveTo(halfStroke, size.height)
                    if (topStartPx > 0f) {
                        lineTo(halfStroke, topStartPx + halfStroke)
                        arcTo(
                            rect = Rect(
                                left = halfStroke,
                                top = halfStroke,
                                right = (topStartPx * 2f) + halfStroke,
                                bottom = (topStartPx * 2f) + halfStroke
                            ),
                            startAngleDegrees = 180f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false
                        )
                    } else {
                        lineTo(halfStroke, halfStroke)
                    }

                    if (topRightPx > 0f) {
                        lineTo(size.width - topRightPx - halfStroke, halfStroke)
                        arcTo(
                            rect = Rect(
                                left = size.width - (topRightPx * 2f) - halfStroke,
                                top = halfStroke,
                                right = size.width - halfStroke,
                                bottom = (topRightPx * 2f) + halfStroke
                            ),
                            startAngleDegrees = 270f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false
                        )
                    } else {
                        lineTo(size.width - halfStroke, halfStroke)
                    }

                    lineTo(size.width - halfStroke, size.height)
                }
                drawPath(
                    path = path,
                    color = borderSubtleColor,
                    style = Stroke(width = strokeWidth)
                )

                val shadowHeightPx = 8.dp.toPx()
                val shadowBrush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.35f)
                    ),
                    startY = size.height - shadowHeightPx,
                    endY = size.height
                )
                drawRect(
                    brush = shadowBrush,
                    topLeft = Offset(0f, size.height - shadowHeightPx),
                    size = Size(size.width, shadowHeightPx)
                )
            }
            .clickable { onExpandWorkoutClick(activeWorkoutBar.workoutId) }
            .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StratumIconButton(
            onClick = { showCancelConfirm = true },
            contentColor = StratumThemeAccessor.colors.error
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Cancel Workout",
                tint = StratumThemeAccessor.colors.error
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = StratumSpacing.s12)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8)
            ) {
                StratumText(
                    text = activeWorkoutBar.workoutName,
                    style = StratumThemeAccessor.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                StratumText(
                    text = elapsedTimeStr,
                    style = StratumThemeAccessor.typography.bodyLarge,
                    muted = true
                )
            }
            StratumText(
                text = activeWorkoutBar.nextLiftName,
                style = StratumThemeAccessor.typography.sectionLabel,
                muted = true,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        StratumIconButton(onClick = { onExpandWorkoutClick(activeWorkoutBar.workoutId) }) {
            Icon(
                painter = painterResource(CoreUiR.drawable.ic_chevron_down),
                contentDescription = "Resume Workout",
                tint = StratumThemeAccessor.colors.textPrimary,
                modifier = Modifier.rotate(180f)
            )
        }
    }

    if (showCancelConfirm) {
        Dialog(onDismissRequest = { showCancelConfirm = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = StratumThemeAccessor.colors.surfaceRaised,
                        shape = StratumThemeAccessor.shapes.panel
                    )
                    .border(
                        width = StratumThemeAccessor.shapes.borderWidth,
                        color = StratumThemeAccessor.colors.borderStrong,
                        shape = StratumThemeAccessor.shapes.panel
                    )
                    .padding(StratumSpacing.s24),
                verticalArrangement = Arrangement.spacedBy(StratumSpacing.s16)
            ) {
                StratumText(
                    text = stringResource(R.string.programs_discard_workout),
                    style = StratumThemeAccessor.typography.titleLarge
                )
                StratumText(
                    text = stringResource(R.string.confirm_discard_dialog_body_text),
                    style = StratumThemeAccessor.typography.bodyLarge,
                    muted = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s12, Alignment.End)
                ) {
                    StratumOutlinedButton(
                        text = stringResource(R.string.programs_cancel_discard),
                        onClick = { showCancelConfirm = false },
                    )
                    StratumButton(
                        text = stringResource(R.string.programs_discard_confirm),
                        onClick = {
                            showCancelConfirm = false
                            onCancelWorkoutClick()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadedProgramContent(
    state: ProgramsUiState.Loaded,
    callbacks: ProgramsScreenCallbacks,
    onSwapLiftClick: (String, WorkoutLiftLineState) -> Unit,
    isPreviewMode: Boolean,
    hasActiveWorkout: Boolean,
    expandedWorkoutId: String?,
    onPendingStart: (ProgramsStartAction) -> Unit,
    onPendingUnskip: (workoutId: String, workoutName: String) -> Unit,
) {
    if (state.upperVolumeByMuscle.isNotEmpty() || state.lowerVolumeByMuscle.isNotEmpty()) {
        val initialTier = if (state.upperVolumeByMuscle.isEmpty() && state.lowerVolumeByMuscle.isNotEmpty()) {
            StratumVolumeBodyTier.Lower
        } else {
            StratumVolumeBodyTier.Upper
        }
        StratumMuscleVolumeRow(
            upperCards = state.upperVolumeByMuscle,
            lowerCards = state.lowerVolumeByMuscle,
            initialTier = initialTier,
        )
    }

    StratumText(
        text = state.program.name,
        style = StratumThemeAccessor.typography.titleLarge,
    )

    state.scheduleItems.forEach { item ->
        when (item) {
            is ScheduleItemState.RestDay -> {
                RestDayRow(
                    isCompleted = item.isCompleted,
                    onCheckedChange = { checked ->
                        callbacks.onSkipRestDayCheckedChange(item.slotIndex, checked)
                    },
                )
            }
            is ScheduleItemState.Workout ->
                WorkoutCard(
                    workout = item.workout,
                    expanded = item.workout.workoutId == expandedWorkoutId,
                    onExpandedChange = { expanded ->
                        callbacks.onExpandedWorkoutChange(
                            if (expanded) item.workout.workoutId else null,
                        )
                    },
                    onStartWorkoutClick = { workoutId ->
                        if (hasActiveWorkout) {
                            onPendingStart(ProgramsStartAction.StartWorkout(workoutId))
                        } else {
                            callbacks.onStartWorkoutClick(workoutId)
                        }
                    },
                    onEditWorkoutClick = callbacks.onEditWorkoutClick,
                    onSkipWorkoutCheckedChange = { workoutId, checked ->
                        if (checked) {
                            callbacks.onSkipWorkoutCheckedChange(workoutId, true)
                        } else {
                            onPendingUnskip(workoutId, item.workout.name)
                        }
                    },
                    onSwapLiftClick = onSwapLiftClick,
                    isPreviewMode = isPreviewMode,
                )
        }
    }
}

@Composable
private fun RestDayRow(
    isCompleted: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
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
                .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s12),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StratumText(text = stringResource(R.string.programs_rest_day), muted = isCompleted)
        StratumCheckbox(
            checked = isCompleted,
            onCheckedChange = onCheckedChange,
        )
    }
}

private val WorkoutCardContentPadding = 20.dp

@Composable
internal fun WorkoutCard(
    workout: WorkoutViewState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onStartWorkoutClick: (String) -> Unit,
    onEditWorkoutClick: (workoutId: String) -> Unit,
    onSkipWorkoutCheckedChange: (workoutId: String, checked: Boolean) -> Unit,
    onSwapLiftClick: (String, WorkoutLiftLineState) -> Unit,
    modifier: Modifier = Modifier,
    isPreviewMode: Boolean = false,
) {
    val sortedLifts = workout.lifts.sortedBy { lift -> lift.position }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "workoutCardChevronRotation",
    )
    val expandCollapseLabel =
        if (expanded) {
            stringResource(R.string.programs_collapse_workout_a11y, workout.name)
        } else {
            stringResource(R.string.programs_expand_workout_a11y, workout.name)
        }
    // No ripple on expand header: default indication draws a hard rectangular flash.
    val headerInteractionSource = remember { MutableInteractionSource() }
    // Completed workouts are de-emphasized — all text renders muted (light grey).
    val muted = workout.isCompleted
    Column(
        modifier =
            modifier
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
                .clip(StratumThemeAccessor.shapes.panel)
                .clickable { onEditWorkoutClick(workout.workoutId) },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = WorkoutCardContentPadding,
                        vertical = StratumSpacing.s16,
                    ),
            horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Entire header (except checkbox) toggles expand — not only the chevron.
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = headerInteractionSource,
                            indication = null,
                            onClick = { onExpandedChange(!expanded) },
                        )
                        .semantics { contentDescription = expandCollapseLabel },
                horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_chevron_down),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(WorkoutChevronSize)
                            .rotate(chevronRotation),
                    tint = StratumThemeAccessor.colors.textMuted,
                )
                StratumText(
                    text = workout.name,
                    style = StratumThemeAccessor.typography.titleLarge,
                    muted = muted,
                    modifier = Modifier.weight(1f),
                )
            }
            StratumCheckbox(
                checked = workout.isCompleted,
                enabled = !workout.isActiveWorkout,
                onCheckedChange = { checked -> onSkipWorkoutCheckedChange(workout.workoutId, checked) },
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                WorkoutCardDivider()
                sortedLifts.forEachIndexed { index, lift ->
                    LiftRow(
                        lift = lift,
                        muted = muted,
                        onSwapLiftClick = { onSwapLiftClick(workout.workoutId, lift) },
                    )
                    if (index < sortedLifts.lastIndex) {
                        WorkoutCardDivider(startPadding = WorkoutCardContentPadding)
                    }
                }
                if (!isPreviewMode) {
                    StratumButton(
                        text = stringResource(R.string.programs_start_workout),
                        onClick = { onStartWorkoutClick(workout.workoutId) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = WorkoutCardContentPadding,
                                    end = WorkoutCardContentPadding,
                                    top = StratumSpacing.s12,
                                    bottom = WorkoutCardContentPadding,
                                ),
                    )
                }
            }
        }
    }
}

private val WorkoutChevronSize = 20.dp

@Composable
private fun LiftRow(
    lift: WorkoutLiftLineState,
    onSwapLiftClick: () -> Unit,
    muted: Boolean = false,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = WorkoutCardContentPadding,
                    vertical = StratumSpacing.s16,
                ),
        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            StratumText(
                text = lift.name,
                style = StratumThemeAccessor.typography.bodyLarge,
                muted = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StratumNumber(
                text = "${lift.setCount} x ${lift.repRange}${lift.topSetRpe}",
                style = StratumThemeAccessor.typography.sectionLabel.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                maxLines = 1,
            )
        }
        StratumIconButton(
            onClick = onSwapLiftClick,
            containerColor = StratumThemeAccessor.colors.surfaceRaised,
            contentColor = StratumThemeAccessor.colors.textMuted,
        ) {
            Icon(
                painter = painterResource(CoreUiR.drawable.ic_swap_horiz),
                contentDescription = stringResource(R.string.programs_swap_lift_content_description),
                tint = StratumThemeAccessor.colors.textMuted,
            )
        }
    }
}

@Composable
private fun WorkoutCardDivider(startPadding: Dp = 0.dp) {
    Row(modifier = Modifier.fillMaxWidth()) {
        if (startPadding > 0.dp) {
            Spacer(modifier = Modifier.width(startPadding))
        }
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(StratumThemeAccessor.shapes.borderWidth)
                    .background(StratumThemeAccessor.colors.borderSubtle),
        )
    }
}

@Composable
private fun UnskipWorkoutDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = StratumThemeAccessor.colors.surfaceRaised,
                    shape = StratumThemeAccessor.shapes.panel,
                )
                .border(
                    width = StratumThemeAccessor.shapes.borderWidth,
                    color = StratumThemeAccessor.colors.borderStrong,
                    shape = StratumThemeAccessor.shapes.panel,
                )
                .padding(StratumSpacing.s24),
            verticalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
        ) {
            StratumText(
                text = stringResource(R.string.programs_unskip_warning_title),
                style = StratumThemeAccessor.typography.titleLarge,
            )
            StratumText(
                text = stringResource(R.string.programs_unskip_warning_body),
                style = StratumThemeAccessor.typography.bodyLarge,
                muted = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s12, Alignment.End),
            ) {
                StratumOutlinedButton(
                    text = stringResource(R.string.programs_unskip_cancel),
                    onClick = onDismiss,
                )
                StratumButton(
                    text = stringResource(R.string.programs_unskip_confirm),
                    onClick = onConfirm,
                )
            }
        }
    }
}



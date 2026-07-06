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
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumCheckbox
import com.stratum.core.ui.component.StratumDropdownMenu
import com.stratum.core.ui.component.StratumDropdownMenuItem
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumMuscleVolumeRow
import com.stratum.core.ui.component.StratumVolumeBodyTier
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumTabScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.program.R

/**
 * Read-only Programs overview. Renders volume summary, weekly schedule with rest days, and workout
 * cards with a header row (name, weekday, skip checkbox) and a flat lift list.
 */
@Composable
internal fun ProgramsScreen(
    state: ProgramsUiState,
    callbacks: ProgramsScreenCallbacks,
    onSwapLiftClick: (WorkoutLiftLineState) -> Unit,
    modifier: Modifier = Modifier,
    isPreviewMode: Boolean = false,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    StratumTabScaffold(
        modifier = modifier,
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
                        StratumIconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                painter = painterResource(CoreUiR.drawable.ic_more_vert),
                                contentDescription = stringResource(R.string.programs_menu_content_description),
                                tint = StratumThemeAccessor.colors.textPrimary,
                            )
                        }
                        StratumDropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            StratumDropdownMenuItem(
                                text = stringResource(R.string.programs_menu_create_program),
                                onClick = {
                                    menuExpanded = false
                                    callbacks.onCreateProgramClick()
                                },
                                leadingIcon = {
                                    ProgramMenuLeadingIcon(
                                        iconRes = CoreUiR.drawable.ic_add,
                                    )
                                },
                            )
                            StratumDropdownMenuItem(
                                text = stringResource(R.string.programs_menu_generate_program),
                                onClick = {
                                    menuExpanded = false
                                    callbacks.onGenerateProgramClick()
                                },
                                leadingIcon = {
                                    ProgramMenuLeadingIcon(
                                        iconRes = CoreUiR.drawable.ic_auto_awesome,
                                    )
                                },
                            )
                            StratumDropdownMenuItem(
                                text = stringResource(R.string.programs_menu_program_library),
                                onClick = {
                                    menuExpanded = false
                                    callbacks.onProgramLibraryClick()
                                },
                                leadingIcon = {
                                    ProgramMenuLeadingIcon(
                                        iconRes = CoreUiR.drawable.ic_library_books,
                                    )
                                },
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
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
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
        horizontalPadding = 12.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!isPreviewMode) {
                StratumButton(
                    text = stringResource(R.string.programs_start_next_workout),
                    onClick = callbacks.onStartNextWorkoutClick,
                    modifier = Modifier.fillMaxWidth(),
                )
                StratumOutlinedButton(
                    text = stringResource(R.string.programs_start_empty_workout),
                    onClick = callbacks.onStartEmptyWorkoutClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            when (state) {
                is ProgramsUiState.Empty ->
                    StratumText(text = stringResource(R.string.programs_empty), muted = true)
                is ProgramsUiState.Loaded -> LoadedProgramContent(
                    state = state,
                    callbacks = callbacks,
                    onSwapLiftClick = onSwapLiftClick,
                    isPreviewMode = isPreviewMode,
                )
            }
        }
    }
}

@Composable
private fun LoadedProgramContent(
    state: ProgramsUiState.Loaded,
    callbacks: ProgramsScreenCallbacks,
    onSwapLiftClick: (WorkoutLiftLineState) -> Unit,
    isPreviewMode: Boolean,
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

    val nextWorkoutId = remember(state.scheduleItems) { findNextWorkoutId(state.scheduleItems) }
    var expandedWorkoutId by rememberSaveable(state.scheduleItems) {
        mutableStateOf(nextWorkoutId)
    }
    LaunchedEffect(nextWorkoutId) {
        expandedWorkoutId = nextWorkoutId
    }

    state.scheduleItems.forEach { item ->
        when (item) {
            is ScheduleItemState.RestDay -> {
                val restDayLabel = stringResource(R.string.programs_rest_day)
                RestDayRow(
                    onCheckedChange = { checked ->
                        callbacks.onSkipWorkoutCheckedChange(restDayLabel, checked)
                    },
                )
            }
            is ScheduleItemState.Workout ->
                WorkoutCard(
                    workout = item.workout,
                    expanded = item.workout.workoutId == expandedWorkoutId,
                    onExpandedChange = { expanded ->
                        expandedWorkoutId = if (expanded) item.workout.workoutId else null
                    },
                    onStartWorkoutClick = callbacks.onStartWorkoutClick,
                    onEditWorkoutClick = callbacks.onEditWorkoutClick,
                    onSkipWorkoutCheckedChange = callbacks.onSkipWorkoutCheckedChange,
                    onSwapLiftClick = onSwapLiftClick,
                    isPreviewMode = isPreviewMode,
                )
        }
    }
}

@Composable
private fun RestDayRow(onCheckedChange: (Boolean) -> Unit) {
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StratumText(text = stringResource(R.string.programs_rest_day))
        StratumCheckbox(
            checked = false,
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
    onSkipWorkoutCheckedChange: (workoutName: String, checked: Boolean) -> Unit,
    onSwapLiftClick: (WorkoutLiftLineState) -> Unit,
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
    val toggleInteractionSource = remember { MutableInteractionSource() }

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
                ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = WorkoutCardContentPadding,
                        vertical = 16.dp,
                    ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics { contentDescription = expandCollapseLabel }
                        .clickable(
                            interactionSource = toggleInteractionSource,
                            indication = null,
                            onClick = { onExpandedChange(!expanded) },
                        ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_chevron_down),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .padding(top = 4.dp)
                            .size(WorkoutChevronSize)
                            .rotate(chevronRotation),
                    tint = StratumThemeAccessor.colors.textMuted,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    StratumText(
                        text = workout.name,
                        style = StratumThemeAccessor.typography.titleLarge,
                        modifier = Modifier.clickable { onEditWorkoutClick(workout.workoutId) },
                    )
                    StratumText(text = workout.weekdayLabel, muted = true)
                }
            }
            StratumCheckbox(
                checked = workout.isCompleted,
                onCheckedChange = { checked -> onSkipWorkoutCheckedChange(workout.name, checked) },
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
                        onSwapLiftClick = { onSwapLiftClick(lift) },
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
                                    top = 12.dp,
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
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = WorkoutCardContentPadding,
                    vertical = 14.dp,
                ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            StratumText(
                text = lift.name,
                style = StratumThemeAccessor.typography.bodyLarge,
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
private fun ProgramMenuLeadingIcon(
    @androidx.annotation.DrawableRes iconRes: Int,
) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        tint = StratumThemeAccessor.colors.textMuted,
    )
}

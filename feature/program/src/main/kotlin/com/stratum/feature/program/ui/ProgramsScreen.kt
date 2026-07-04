package com.stratum.feature.program.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
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
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.program.R

/**
 * Read-only Programs overview. Renders volume summary, weekly schedule with rest days, and verbose
 * workout cards. All controls are present but no-op until wired in a later slice.
 *
 * Lift-swap surface: tapping a lift sub-card opens [StratumLiftSwapBottomSheet] via
 * [swapBindings]; the bindings bundle the sheet state + every swap-related callback so this
 * composable's signature stays under the detekt parameter threshold.
 */
@Composable
internal fun ProgramsScreen(
    state: ProgramsUiState,
    onCalendarClick: () -> Unit,
    onCreateProgramClick: () -> Unit,
    onGenerateProgramClick: () -> Unit,
    onProgramLibraryClick: () -> Unit,
    onStartNextWorkoutClick: () -> Unit,
    onStartEmptyWorkoutClick: () -> Unit,
    onStartWorkoutClick: (workoutName: String) -> Unit,
    onSkipWorkoutCheckedChange: (workoutName: String, checked: Boolean) -> Unit,
    onSwapLiftClick: (WorkoutLiftLineState) -> Unit,
    modifier: Modifier = Modifier,
    isPreviewMode: Boolean = false,
    onKeepProgramClick: () -> Unit = {},
    onDiscardProgramClick: () -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }

    StratumScaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            StratumTopBar(
                title = stringResource(R.string.programs_screen_title),
                navigationIcon = if (isPreviewMode) null else {
                    {
                        StratumIconButton(onClick = onCalendarClick) {
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
                                    onCreateProgramClick()
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
                                    onGenerateProgramClick()
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
                                    onProgramLibraryClick()
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
                        onClick = onKeepProgramClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    StratumOutlinedButton(
                        text = stringResource(R.string.programs_discard_program),
                        onClick = onDiscardProgramClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding(),
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!isPreviewMode) {
                StratumButton(
                    text = stringResource(R.string.programs_start_next_workout),
                    onClick = onStartNextWorkoutClick,
                    modifier = Modifier.fillMaxWidth(),
                )
                StratumOutlinedButton(
                    text = stringResource(R.string.programs_start_empty_workout),
                    onClick = onStartEmptyWorkoutClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            when (state) {
                is ProgramsUiState.Empty ->
                    StratumText(text = stringResource(R.string.programs_empty), muted = true)
                is ProgramsUiState.Loaded -> LoadedProgramContent(
                    state = state,
                    onStartWorkoutClick = onStartWorkoutClick,
                    onSkipWorkoutCheckedChange = onSkipWorkoutCheckedChange,
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
    onStartWorkoutClick: (String) -> Unit,
    onSkipWorkoutCheckedChange: (workoutName: String, checked: Boolean) -> Unit,
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

    state.scheduleItems.forEach { item ->
        when (item) {
            is ScheduleItemState.RestDay -> {
                val restDayLabel = stringResource(R.string.programs_rest_day)
                RestDayRow(
                    onCheckedChange = { checked ->
                        onSkipWorkoutCheckedChange(restDayLabel, checked)
                    },
                )
            }
            is ScheduleItemState.Workout ->
                WorkoutCard(
                    workout = item.workout,
                    onStartWorkoutClick = onStartWorkoutClick,
                    onSkipWorkoutCheckedChange = onSkipWorkoutCheckedChange,
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

@Composable
internal fun WorkoutCard(
    workout: WorkoutViewState,
    onStartWorkoutClick: (String) -> Unit,
    onSkipWorkoutCheckedChange: (workoutName: String, checked: Boolean) -> Unit,
    onSwapLiftClick: (WorkoutLiftLineState) -> Unit,
    isPreviewMode: Boolean = false,
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
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StratumText(text = workout.name, style = StratumThemeAccessor.typography.titleLarge)
                StratumText(text = workout.weekdayLabel, muted = true)
            }
            StratumCheckbox(
                checked = workout.isCompleted,
                onCheckedChange = { checked -> onSkipWorkoutCheckedChange(workout.name, checked) },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            workout.lifts.sortedBy { lift -> lift.position }.forEach { lift ->
                LiftSubCard(lift = lift, onSwapLiftClick = { onSwapLiftClick(lift) })
            }
        }
        if (!isPreviewMode) {
            StratumButton(
                text = stringResource(R.string.programs_start_workout),
                onClick = { onStartWorkoutClick(workout.name) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Two-row bordered sub card for a single lift inside a workout card. Row 1 is the lift name
 * (truncates with an ellipsis when the name is too long to fit); row 2 is the tabular
 * `sets x repRange@rpe` summary, which never gets truncated because the name no longer
 * competes for the same line. The trailing swap icon opens the lift swap bottom sheet —
 * the card itself is not clickable so the rest of the row is inert.
 */
@Composable
private fun LiftSubCard(
    lift: WorkoutLiftLineState,
    onSwapLiftClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color = StratumThemeAccessor.colors.surfaceInset,
                    shape = StratumThemeAccessor.shapes.chip,
                )
                .border(
                    width = StratumThemeAccessor.shapes.borderWidth,
                    color = StratumThemeAccessor.colors.borderSubtle,
                    shape = StratumThemeAccessor.shapes.chip,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
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
private fun ProgramMenuLeadingIcon(
    @androidx.annotation.DrawableRes iconRes: Int,
) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        tint = StratumThemeAccessor.colors.textMuted,
    )
}

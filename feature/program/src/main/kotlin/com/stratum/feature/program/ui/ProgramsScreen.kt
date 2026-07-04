package com.stratum.feature.program.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
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
 */
@Composable
fun ProgramsScreen(
    state: ProgramsUiState,
    onCalendarClick: () -> Unit,
    onCreateProgramClick: () -> Unit,
    onGenerateProgramClick: () -> Unit,
    onProgramLibraryClick: () -> Unit,
    onStartNextWorkoutClick: () -> Unit,
    onStartEmptyWorkoutClick: () -> Unit,
    onStartWorkoutClick: (workoutName: String) -> Unit,
    onSkipWorkoutCheckedChange: (workoutName: String, checked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    // TEMP: back-to-onboarding for regeneration testing
    onBackToGenerateClick: () -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }

    StratumScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            StratumTopBar(
                title = stringResource(R.string.programs_screen_title),
                navigationIcon = {
                    StratumIconButton(onClick = onCalendarClick) {
                        Icon(
                            painter = painterResource(CoreUiR.drawable.ic_calendar),
                            contentDescription = stringResource(R.string.programs_calendar_content_description),
                            tint = StratumThemeAccessor.colors.textPrimary,
                        )
                    }
                },
                actions = {
                    // TEMP: back-to-onboarding for regeneration testing
                    StratumIconButton(onClick = onBackToGenerateClick) {
                        Icon(
                            painter = painterResource(CoreUiR.drawable.ic_auto_awesome),
                            contentDescription = stringResource(R.string.programs_back_to_generate_content_description),
                            tint = StratumThemeAccessor.colors.textPrimary,
                        )
                    }
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
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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

            when (state) {
                is ProgramsUiState.Empty ->
                    StratumText(text = stringResource(R.string.programs_empty), muted = true)
                is ProgramsUiState.Loaded -> LoadedProgramContent(
                    state = state,
                    onStartWorkoutClick = onStartWorkoutClick,
                    onSkipWorkoutCheckedChange = onSkipWorkoutCheckedChange,
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
private fun WorkoutCard(
    workout: WorkoutViewState,
    onStartWorkoutClick: (String) -> Unit,
    onSkipWorkoutCheckedChange: (workoutName: String, checked: Boolean) -> Unit,
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
        workout.lifts.sortedBy { lift -> lift.position }.forEach { lift ->
            MarqueeLiftLine(text = lift.summaryLine)
        }
        StratumButton(
            text = stringResource(R.string.programs_start_workout),
            onClick = { onStartWorkoutClick(workout.name) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MarqueeLiftLine(text: String) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    var textWidthPx by remember { mutableStateOf(0) }
    var containerWidthPx by remember { mutableStateOf(0) }
    // Require a meaningful amount of overflow before marqueeing, otherwise lines that
    // fit within a pixel or two end up "wiggling" with no real distance to travel.
    val truncated =
        containerWidthPx > 0 && textWidthPx - containerWidthPx > MARQUEE_TRUNCATION_THRESHOLD_PX

    LaunchedEffect(truncated) {
        if (!truncated) return@LaunchedEffect
        // Slowly scroll the line left so the ellipsis follows the text and the cut-off
        // tail comes into view, then reset. Repeats for the lifetime of the composable.
        while (true) {
            delay(MARQUEE_PAUSE_BEFORE_MS)
            scrollState.animateScrollTo(
                value = scrollState.maxValue,
                animationSpec = tween(
                    durationMillis = MARQUEE_SCROLL_MS_PER_PX * scrollState.maxValue.coerceAtLeast(1),
                    easing = LinearEasing,
                ),
            )
            delay(MARQUEE_PAUSE_AFTER_MS)
            scrollState.animateScrollTo(
                value = 0,
                animationSpec = tween(
                    durationMillis = MARQUEE_RESET_MS,
                    easing = LinearEasing,
                ),
            )
        }
    }

    BoxWithConstraints {
        containerWidthPx = with(density) { maxWidth.toPx() }.toInt()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
        ) {
            StratumNumber(
                text = text,
                style = StratumThemeAccessor.typography.sectionLabel.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                maxLines = 1,
                onTextLayout = { result: TextLayoutResult ->
                    textWidthPx = result.size.width
                },
            )
        }
    }
}

private const val MARQUEE_PAUSE_BEFORE_MS = 1_500L
private const val MARQUEE_PAUSE_AFTER_MS = 2_000L
private const val MARQUEE_SCROLL_MS_PER_PX = 45
private const val MARQUEE_RESET_MS = 600
private const val MARQUEE_TRUNCATION_THRESHOLD_PX = 8

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

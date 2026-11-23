package com.stratum.feature.program.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.stratum.core.ui.component.PanePosture
import com.stratum.core.ui.component.StratumActiveBadge
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTwoPane
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.program.R

/**
 * Unfolded Program screen (STR-332). Left pane: the start actions, volume strip, program
 * name, and the week as compact day rows. Right pane: the selected day opened full, the
 * same card the single pane expands, so swap, skip, and start act exactly as they do there.
 */
@Composable
internal fun ProgramsTwoPane(
    posture: PanePosture,
    state: ProgramsUiState.Loaded,
    callbacks: ProgramsScreenCallbacks,
    listScrollState: ScrollState,
    contentPadding: PaddingValues,
    header: @Composable () -> Unit,
    onSwapLiftClick: (String, WorkoutLiftLineState) -> Unit,
    isPreviewMode: Boolean,
    hasActiveWorkout: Boolean,
    onPendingStart: (ProgramsStartAction) -> Unit,
    onPendingUnskip: (workoutId: String, workoutName: String) -> Unit,
) {
    val nextWorkoutId = findNextWorkoutId(state.scheduleItems)
    val selectedWorkoutId = resolveSelectedWorkoutId(state.scheduleItems, state.selectedWorkoutId)
    val selectedWorkout =
        state.scheduleItems
            .filterIsInstance<ScheduleItemState.Workout>()
            .firstOrNull { item -> item.workout.workoutId == selectedWorkoutId }
            ?.workout
    StratumTwoPane(
        posture = posture,
        primary = {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(listScrollState)
                        .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
            ) {
                header()
                ProgramSummaryHeader(state = state)
                ProgramWeekRows(
                    scheduleItems = state.scheduleItems,
                    selectedWorkoutId = selectedWorkoutId,
                    nextWorkoutId = nextWorkoutId,
                    onSelectWorkout = callbacks.onSelectWorkout,
                    onSkipRestDayCheckedChange = callbacks.onSkipRestDayCheckedChange,
                )
            }
        },
        detail = {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(contentPadding),
            ) {
                if (selectedWorkout == null) {
                    StratumText(
                        text = stringResource(R.string.programs_day_pane_empty),
                        muted = true,
                    )
                } else {
                    key(selectedWorkout.workoutId) {
                        WorkoutCard(
                            workout = selectedWorkout,
                            expanded = true,
                            onExpandedChange = {},
                            onStartWorkoutClick =
                                startWorkoutAction(callbacks, hasActiveWorkout, onPendingStart),
                            onEditWorkoutClick = callbacks.onEditWorkoutClick,
                            onSkipWorkoutCheckedChange =
                                skipWorkoutAction(callbacks, selectedWorkout.name, onPendingUnskip),
                            onSwapLiftClick = onSwapLiftClick,
                            isPreviewMode = isPreviewMode,
                            collapsible = false,
                        )
                    }
                }
            }
        },
    )
}

/** The week in cycle order: selectable workout rows and muted rest rows. */
@Composable
private fun ProgramWeekRows(
    scheduleItems: List<ScheduleItemState>,
    selectedWorkoutId: String?,
    nextWorkoutId: String?,
    onSelectWorkout: (String) -> Unit,
    onSkipRestDayCheckedChange: (slotIndex: Int, checked: Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8)) {
        scheduleItems.forEach { item ->
            when (item) {
                is ScheduleItemState.RestDay ->
                    RestDayRow(
                        isCompleted = item.isCompleted,
                        onCheckedChange = { checked ->
                            onSkipRestDayCheckedChange(item.slotIndex, checked)
                        },
                        alwaysMuted = true,
                    )
                is ScheduleItemState.Workout ->
                    WorkoutDayRow(
                        workout = item.workout,
                        selected = item.workout.workoutId == selectedWorkoutId,
                        isNext = item.workout.workoutId == nextWorkoutId,
                        onClick = { onSelectWorkout(item.workout.workoutId) },
                    )
            }
        }
    }
}

@Composable
private fun WorkoutDayRow(
    workout: WorkoutViewState,
    selected: Boolean,
    isNext: Boolean,
    onClick: () -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    // Skipped or done this cycle: muted, same as the single-pane card.
    val muted = workout.isCompleted
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = WorkoutCardHeaderMinHeight)
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(
                    width = shapes.borderWidth,
                    color = if (selected) colors.accent else colors.borderSubtle,
                    shape = shapes.panel,
                )
                .clip(shapes.panel)
                .semantics { this.selected = selected }
                .clickable(role = Role.Tab, onClick = onClick)
                .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s12),
        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            StratumText(
                text = workout.name,
                style = StratumThemeAccessor.typography.titleMedium,
                muted = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StratumText(
                text =
                    pluralStringResource(
                        R.plurals.programs_day_lift_count,
                        workout.lifts.size,
                        workout.lifts.size,
                    ),
                style = StratumThemeAccessor.typography.sectionLabel,
                muted = true,
            )
        }
        when {
            isNext -> StratumActiveBadge(label = stringResource(R.string.programs_day_next), active = true)
            workout.isCompleted ->
                StratumText(
                    text = stringResource(R.string.programs_day_done),
                    style = StratumThemeAccessor.typography.sectionLabel,
                    muted = true,
                )
        }
    }
}

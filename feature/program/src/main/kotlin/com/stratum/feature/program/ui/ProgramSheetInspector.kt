package com.stratum.feature.program.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.program.CreateProgramDayUi
import com.stratum.feature.program.CreateProgramUiState
import com.stratum.feature.program.ProgramSheetClipboard
import com.stratum.feature.program.ProgramSheetInspectorDashboard
import com.stratum.feature.program.ProgramSheetLiftTarget
import com.stratum.feature.program.ProgramSheetSelection
import com.stratum.feature.program.R
import com.stratum.feature.program.formatEstimatedWorkoutDuration
import com.stratum.feature.program.formatSheetSetCount
import com.stratum.feature.program.inspectorLiftContribution
import com.stratum.feature.program.programSheetInspectorDashboard
import com.stratum.feature.program.programSheetMetricsFromEditorDays

/**
 * Right pane. Clipboard and multi-select take the pane. Otherwise the selected
 * lift card, or the dashboard. Numbers come from [programSheetInspectorDashboard].
 */
@Composable
internal fun ProgramSheetInspector(
    state: CreateProgramUiState,
    selected: ProgramSheetLiftTarget?,
    clipboard: ProgramSheetClipboard?,
    selection: ProgramSheetSelection,
    callbacks: CreateProgramScreenCallbacks,
    dockHost: EditWorkoutDockHostState,
    onCloseLift: () -> Unit,
    onMoveLiftTo: (String) -> Unit,
    onRirClick: (EditSetState) -> Unit,
    onShowLiftActions: (String) -> Unit,
    onShowProgressionSchemeInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .background(StratumThemeAccessor.colors.surfaceBase),
    ) {
        when {
            selection.active ->
                ProgramSheetBulkHost(
                    days = state.days,
                    selection = selection.ordered,
                    dockHost = dockHost,
                    onAddSet = callbacks.onAddSetToSheetSelection,
                    onRemoveSet = callbacks.onRemoveSetFromSheetSelection,
                    onSetRir = callbacks.onSetRirOnSheetSelection,
                    onSetRepBand = callbacks.onSetRepBandOnSheetSelection,
                    onMove = callbacks.onMoveSheetSelection,
                    onDelete = callbacks.onDeleteSheetSelection,
                    onCancel = callbacks.onCancelSheetSelection,
                )
            clipboard != null ->
                ProgramSheetClipboardTray(
                    clipboard = clipboard,
                    onCancel = callbacks.onCancelSheetClipboard,
                )
            selected != null ->
                ProgramSheetInspectorLift(
                    state = state,
                    selected = selected,
                    callbacks = callbacks,
                    onCloseLift = onCloseLift,
                    onMoveLiftTo = onMoveLiftTo,
                    onRirClick = onRirClick,
                    onShowLiftActions = onShowLiftActions,
                    onShowProgressionSchemeInfo = onShowProgressionSchemeInfo,
                )
            else -> ProgramSheetInspectorDashboard(state = state)
        }
    }
}

@Composable
private fun ProgramSheetInspectorDashboard(
    state: CreateProgramUiState,
    modifier: Modifier = Modifier,
) {
    val metrics = remember(state.days) { programSheetMetricsFromEditorDays(state.days) }
    val dashboard = remember(metrics, state.days) { programSheetInspectorDashboard(metrics, state.days) }
    val bars = remember(dashboard.muscles) { dashboard.muscles.sortedByDescending { muscle -> muscle.sets } }
    val maxSets = bars.maxOfOrNull { muscle -> muscle.sets } ?: 0.0
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(StratumSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
    ) {
        StratumText(
            text =
                pluralStringResource(
                    R.plurals.create_program_sheet_set_total,
                    dashboard.programSetTotal,
                    dashboard.programSetTotal,
                ),
            style = StratumThemeAccessor.typography.titleLarge,
        )
        if (bars.isNotEmpty()) {
            StratumText(
                text = stringResource(R.string.create_program_sheet_weekly_volume),
                style = StratumThemeAccessor.typography.titleMedium,
            )
            for (muscle in bars) {
                InspectorVolumeBar(name = muscle.muscleName, sets = muscle.sets, maxSets = maxSets)
            }
        }
        StratumText(
            text = stringResource(R.string.create_program_sheet_volume_by_workout),
            style = StratumThemeAccessor.typography.titleMedium,
        )
        ProgramSheetVolumePivotTable(pivot = dashboard.pivot)
        StratumText(
            text = stringResource(R.string.create_program_sheet_workouts),
            style = StratumThemeAccessor.typography.titleMedium,
        )
        for (workout in dashboard.workouts) {
            val setsLabel =
                pluralStringResource(
                    R.plurals.create_program_sheet_set_total,
                    workout.setTotal,
                    workout.setTotal,
                )
            StratumText(
                text = workout.name,
                style = StratumThemeAccessor.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StratumText(
                text =
                    stringResource(
                        R.string.create_program_sheet_workout_meta,
                        setsLabel,
                        formatEstimatedWorkoutDuration(workout.estimatedDurationMinutes),
                    ),
                muted = true,
            )
        }
    }
}

@Composable
private fun InspectorVolumeBar(
    name: String,
    sets: Double,
    maxSets: Double,
) {
    val fraction =
        if (maxSets <= 0.0) {
            0f
        } else {
            (sets / maxSets).toFloat().coerceIn(0f, 1f)
        }
    val colors = StratumThemeAccessor.colors
    Column(verticalArrangement = Arrangement.spacedBy(StratumSpacing.s4)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StratumText(
                text = name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StratumNumber(text = formatSheetSetCount(sets))
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(colors.surfaceInset),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(colors.accent),
            )
        }
    }
}

@Composable
private fun ProgramSheetInspectorLift(
    state: CreateProgramUiState,
    selected: ProgramSheetLiftTarget,
    callbacks: CreateProgramScreenCallbacks,
    onCloseLift: () -> Unit,
    onMoveLiftTo: (String) -> Unit,
    onRirClick: (EditSetState) -> Unit,
    onShowLiftActions: (String) -> Unit,
    onShowProgressionSchemeInfo: () -> Unit,
) {
    val day = state.days.firstOrNull { it.id == selected.dayId }
    val edit = day?.editState
    val lift = edit?.lifts?.firstOrNull { it.workoutLiftId == selected.workoutLiftId }
    if (day == null || edit == null || lift == null) {
        ProgramSheetInspectorDashboard(state = state)
        return
    }
    val metrics = remember(state.days) { programSheetMetricsFromEditorDays(state.days) }
    val dashboard = remember(metrics, state.days) { programSheetInspectorDashboard(metrics, state.days) }
    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = StratumSpacing.s8),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
    ) {
        InspectorLiftHeader(lift = lift, workoutName = day.name)
        ProgramSheetExpandedLift(
            lift = lift,
            allLifts = edit.lifts,
            callbacks = editWorkoutCallbacks(callbacks).scopedToDay(day.id, callbacks.onSelectDay),
            onCollapse = onCloseLift,
            onShowLiftActions = { onShowLiftActions(lift.workoutLiftId) },
            onRirClick = onRirClick,
            onShowProgressionSchemeInfo = onShowProgressionSchemeInfo,
        )
        InspectorLiftContributionBlock(lift = lift, workoutName = day.name, dashboard = dashboard, dayId = day.id)
        InspectorLiftMoveTo(days = state.days, currentDayId = day.id, onMoveLiftTo = onMoveLiftTo)
    }
}

/** The mockup's contribution block: labeled credits, program totals, workout total. */
@Composable
private fun InspectorLiftContributionBlock(
    lift: EditLiftState,
    workoutName: String,
    dashboard: ProgramSheetInspectorDashboard,
    dayId: String,
) {
    if (lift.muscleCredits.isEmpty()) return
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val contribution =
        remember(lift.muscleCredits, dashboard, dayId) {
            inspectorLiftContribution(credits = lift.muscleCredits, dashboard = dashboard, dayId = dayId)
        }
    Column(
        modifier =
            Modifier
                .padding(horizontal = StratumSpacing.s16)
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .padding(StratumSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
    ) {
        StratumText(
            text = stringResource(R.string.create_program_sheet_contributes),
            style = StratumThemeAccessor.typography.titleMedium,
        )
        for (row in contribution.rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StratumText(text = row.muscleName, modifier = Modifier.weight(1f))
                StratumText(
                    text =
                        stringResource(
                            if (row.primary) {
                                R.string.create_program_sheet_primary
                            } else {
                                R.string.create_program_sheet_secondary
                            },
                        ),
                    muted = true,
                )
                StratumNumber(
                    text = "+${formatSheetSetCount(row.creditSets)}",
                    modifier = Modifier.padding(start = StratumSpacing.s8),
                )
            }
        }
        Spacer(modifier = Modifier.height(StratumSpacing.s8))
        StratumText(
            text = stringResource(R.string.create_program_sheet_program_totals),
            style = StratumThemeAccessor.typography.labelLarge,
            muted = true,
        )
        StratumText(
            text =
                contribution.rows.joinToString(separator = " · ") { row ->
                    "${row.muscleName} ${formatSheetSetCount(row.programSets)}"
                },
        )
        Spacer(modifier = Modifier.height(StratumSpacing.s8))
        StratumText(
            text =
                stringResource(
                    R.string.create_program_sheet_workout_set_total,
                    workoutName,
                ),
            style = StratumThemeAccessor.typography.labelLarge,
            muted = true,
        )
        StratumText(
            text =
                pluralStringResource(
                    R.plurals.create_program_sheet_set_total,
                    contribution.workoutSetTotal,
                    contribution.workoutSetTotal,
                ),
        )
    }
}

/** One button per workout; the current workout is marked and inert. */
@Composable
private fun InspectorLiftMoveTo(
    days: List<CreateProgramDayUi>,
    currentDayId: String,
    onMoveLiftTo: (String) -> Unit,
) {
    val targets = days.filter { day -> !day.isRest && day.editState != null }
    if (targets.size < 2) return
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Column(
        modifier = Modifier.padding(horizontal = StratumSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
    ) {
        StratumText(
            text = stringResource(R.string.create_program_sheet_move_to),
            style = StratumThemeAccessor.typography.titleMedium,
        )
        for (day in targets) {
            val current = day.id == currentDayId
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .background(
                            color = if (current) colors.accentMuted else colors.surfaceRaised,
                            shape = shapes.panel,
                        )
                        .border(width = shapes.borderWidth, color = colors.borderSubtle, shape = shapes.panel)
                        .then(
                            if (current) {
                                Modifier
                            } else {
                                Modifier.clickable { onMoveLiftTo(day.id) }
                            },
                        )
                        .padding(horizontal = StratumSpacing.s16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StratumText(
                    text =
                        if (current) {
                            stringResource(R.string.create_program_sheet_move_here, day.name)
                        } else {
                            day.name
                        },
                    color = if (current) colors.onAccentMuted else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InspectorLiftHeader(
    lift: EditLiftState,
    workoutName: String,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val primary = lift.muscleCredits.filter { credit -> credit.primary }
    val secondary = lift.muscleCredits.filter { credit -> !credit.primary }
    Column(
        modifier = Modifier.padding(horizontal = StratumSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
    ) {
        StratumText(
            text = lift.name,
            style = StratumThemeAccessor.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        StratumText(
            text = workoutName,
            muted = true,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (lift.muscleCredits.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
                verticalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
            ) {
                for (credit in primary) {
                    InspectorMuscleChip(
                        name = credit.muscleName,
                        container = colors.accentMuted,
                        content = colors.onAccentMuted,
                        shape = shapes.chip,
                    )
                }
                for (credit in secondary) {
                    InspectorMuscleChip(
                        name = credit.muscleName,
                        container = colors.surfaceRaised,
                        content = colors.textPrimary,
                        shape = shapes.chip,
                    )
                }
            }
        }
    }
}

@Composable
private fun InspectorMuscleChip(
    name: String,
    container: Color,
    content: Color,
    shape: Shape,
) {
    StratumText(
        text = name,
        color = content,
        style = StratumThemeAccessor.typography.sectionLabel,
        maxLines = 1,
        modifier =
            Modifier
                .background(color = container, shape = shape)
                .padding(horizontal = StratumSpacing.s8, vertical = StratumSpacing.s4),
    )
}

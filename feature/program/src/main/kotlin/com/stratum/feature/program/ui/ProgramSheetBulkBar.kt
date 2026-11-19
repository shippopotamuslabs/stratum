package com.stratum.feature.program.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.RirTargetSheetContent
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumCheckbox
import com.stratum.core.ui.component.StratumTextButton
import com.stratum.core.ui.component.workoutlog.RepRangeKeyboardField
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.rememberStratumHaptics
import com.stratum.core.ui.theme.stratumDockTopRule
import com.stratum.feature.program.CreateProgramDayUi
import com.stratum.feature.program.PROGRAM_SHEET_BULK_REP_SET_ID
import com.stratum.feature.program.ProgramSheetLiftRef
import com.stratum.feature.program.ProgramSheetPadAction
import com.stratum.feature.program.ProgramSheetSelection
import com.stratum.feature.program.R
import com.stratum.feature.program.findProgramSheetLift

/** Sheet multi-select. Idle unless the lifter has entered select mode. Not part of the draft. */
internal val LocalProgramSheetSelection =
    staticCompositionLocalOf { ProgramSheetSelection() }

internal val ProgramSheetSelectionSlot = 48.dp

internal fun EditWorkoutDockHostState.applySheetPad(action: ProgramSheetPadAction) {
    when (action) {
        ProgramSheetPadAction.CommitIfValidThenClose -> commitIfValidThenClose()
        ProgramSheetPadAction.DiscardWithoutCommit -> discardWithoutCommit()
        ProgramSheetPadAction.None -> Unit
    }
}

/**
 * Bulk bar plus the RIR sheet, rep pad, and move picker it opens.
 * Sits where the clipboard tray sits. Cancel does not change lifts.
 */
@Composable
internal fun ProgramSheetBulkHost(
    days: List<CreateProgramDayUi>,
    selection: List<ProgramSheetLiftRef>,
    dockHost: EditWorkoutDockHostState,
    onAddSet: (String) -> Unit,
    onRemoveSet: (String) -> Unit,
    onSetRir: (Int, String) -> Unit,
    onSetRepBand: (Int, Int, String) -> Unit,
    onMove: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val enabled = selection.isNotEmpty()
    var showRir by rememberSaveable { mutableStateOf(false) }
    var showMove by rememberSaveable { mutableStateOf(false) }
    val addedSetMessage = stringResource(R.string.create_program_sheet_added_set)
    val removedSetMessage = stringResource(R.string.create_program_sheet_removed_set)
    val movedMessage = stringResource(R.string.create_program_sheet_moved_selection)
    val removedMessage = stringResource(R.string.create_program_sheet_removed_selection)
    ProgramSheetBulkBar(
        enabled = enabled,
        onAddSet = {
            dockHost.discardWithoutCommit()
            onAddSet(addedSetMessage)
        },
        onRemoveSet = {
            dockHost.discardWithoutCommit()
            onRemoveSet(removedSetMessage)
        },
        onSetRir = {
            if (!enabled) return@ProgramSheetBulkBar
            dockHost.discardWithoutCommit()
            showRir = true
        },
        onSetRepRange = {
            if (!enabled) return@ProgramSheetBulkBar
            val (min, max) = programSheetBulkRepSeed(days, selection)
            dockHost.openRepRange(
                setId = PROGRAM_SHEET_BULK_REP_SET_ID,
                field = RepRangeKeyboardField.Min,
                minText = min.toString(),
                maxText = max.toString(),
                onCommitBand = { repMin, repMax ->
                    onSetRepBand(
                        repMin,
                        repMax,
                        context.getString(R.string.create_program_sheet_set_reps_done, repMin, repMax),
                    )
                },
            )
        },
        onMove = {
            if (!enabled) return@ProgramSheetBulkBar
            dockHost.discardWithoutCommit()
            showMove = true
        },
        onDelete = {
            dockHost.discardWithoutCommit()
            onDelete(removedMessage)
        },
        onCancel = {
            dockHost.discardWithoutCommit()
            showRir = false
            showMove = false
            onCancel()
        },
    )
    if (showRir) {
        val selectedRir = programSheetSelectionRir(days, selection)
        StratumBottomSheet(onDismissRequest = { showRir = false }) {
            RirTargetSheetContent(
                selectedRirGoal = selectedRir,
                onRirSelected = { rir ->
                    onSetRir(rir, context.getString(R.string.create_program_sheet_set_rir_done, rir))
                },
                onDismiss = { showRir = false },
            )
        }
    }
    if (showMove) {
        ProgramSheetMoveToSheet(
            days = days,
            onMoveTo = { dayId ->
                showMove = false
                onMove(dayId, movedMessage)
            },
            onDismiss = { showMove = false },
        )
    }
}

@Composable
private fun ProgramSheetBulkBar(
    enabled: Boolean,
    onAddSet: () -> Unit,
    onRemoveSet: () -> Unit,
    onSetRir: () -> Unit,
    onSetRepRange: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val deleteColor = StratumThemeAccessor.colors.error
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(StratumThemeAccessor.colors.surfaceRaised)
                .stratumDockTopRule()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = StratumSpacing.s8, vertical = StratumSpacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StratumTextButton(
            text = stringResource(R.string.create_program_sheet_add_one_set),
            onClick = onAddSet,
            enabled = enabled,
        )
        StratumTextButton(
            text = stringResource(R.string.create_program_sheet_remove_one_set),
            onClick = onRemoveSet,
            enabled = enabled,
        )
        StratumTextButton(
            text = stringResource(R.string.create_program_sheet_set_rir),
            onClick = onSetRir,
            enabled = enabled,
        )
        StratumTextButton(
            text = stringResource(R.string.create_program_sheet_set_rep_range),
            onClick = onSetRepRange,
            enabled = enabled,
        )
        StratumTextButton(
            text = stringResource(R.string.create_program_sheet_move_to),
            onClick = onMove,
            enabled = enabled,
        )
        StratumTextButton(
            text = stringResource(R.string.create_program_sheet_delete),
            onClick = onDelete,
            enabled = enabled,
            contentColor = deleteColor,
        )
        StratumTextButton(
            text = stringResource(R.string.create_program_sheet_cancel),
            onClick = onCancel,
        )
    }
}

@Composable
internal fun ProgramSheetSelectableLiftRow(
    lift: EditLiftState,
    zebra: Boolean,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val background =
        if (zebra) {
            StratumThemeAccessor.colors.surfaceSunken
        } else {
            StratumThemeAccessor.colors.surfaceBase
        }
    val haptics = rememberStratumHaptics()
    val toggle = {
        haptics.toggle(!checked)
        onToggle()
    }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(background)
                .sheetHairline()
                .combinedClickable(role = Role.Checkbox, onClick = toggle, onLongClick = toggle)
                .semantics {
                    toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                }
                .heightIn(min = 48.dp)
                .padding(end = StratumSpacing.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(ProgramSheetSelectionSlot),
            contentAlignment = Alignment.Center,
        ) {
            StratumCheckbox(checked = checked, onCheckedChange = null)
        }
        SheetLine(
            lift = lift.name,
            sets = lift.sets.size.toString(),
            reps = sheetRepLabel(lift.sets),
            rir = sheetRirLabel(lift.sets),
            rest = sheetRestLabel(lift.sets),
            header = false,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun ProgramSheetMoveToSheet(
    days: List<CreateProgramDayUi>,
    onMoveTo: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    StratumBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.create_program_sheet_move_to),
    ) {
        days.filter { day -> !day.isRest && day.editState != null }.forEach { day ->
            CreateProgramOptionRow(
                text = day.name,
                leadingIcon = rememberVectorPainter(Icons.Outlined.FitnessCenter),
                onClick = { onMoveTo(day.id) },
            )
        }
    }
}

/** Band shown in the bulk rep pad. Prefers the first selected set that already has reps. */
internal fun programSheetBulkRepSeed(
    days: List<CreateProgramDayUi>,
    selection: List<ProgramSheetLiftRef>,
): Pair<Int, Int> {
    val lift = selection.firstNotNullOfOrNull { ref -> findProgramSheetLift(days, ref) }
    val set = lift?.sets?.firstOrNull { item -> item.repMin > 0 || item.repMax > 0 } ?: lift?.sets?.firstOrNull()
    val min = set?.repMin?.takeIf { it > 0 } ?: DEFAULT_REP_MIN
    val max = set?.repMax?.takeIf { it > 0 } ?: DEFAULT_REP_MAX
    return min to max
}

internal fun programSheetSelectionRir(
    days: List<CreateProgramDayUi>,
    selection: List<ProgramSheetLiftRef>,
): Int {
    val lift = selection.firstNotNullOfOrNull { ref -> findProgramSheetLift(days, ref) }
    return lift?.sets?.firstOrNull()?.rirGoal ?: DEFAULT_RIR_GOAL
}

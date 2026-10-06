package com.stratum.feature.workout.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.LiftActionsSheetContent
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumDurationStepper
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.SupersetLinkBottomSheet
import com.stratum.core.ui.component.SupersetLinkDirection
import com.stratum.core.ui.component.isLastInSupersetChain
import com.stratum.core.ui.component.isSupersetPairMember
import com.stratum.core.ui.component.shouldKeepSupersetSheetOpenAfterLink
import com.stratum.core.ui.component.supersetLinkSheetOptions
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.workout.R
import com.stratum.feature.workout.notification.RestTimerDefaults

internal const val MIN_CONFIGURE_REST_SECONDS = 0
internal const val MAX_CONFIGURE_REST_SECONDS = 600
internal const val CONFIGURE_REST_SECONDS_STEP = 15

internal data class LiftNotesSheetHostState(
    val liftSheetMode: LiftOptionsSheetMode,
    val onLiftSheetModeChange: (LiftOptionsSheetMode) -> Unit,
    val pastSessionNotesLiftId: String?,
    val onPastSessionNotesLiftIdChange: (String?) -> Unit,
    val onEditNotes: (EditingNotesTarget) -> Unit,
)

/** Mutable sheet/dialog flags for [WorkoutSessionOverlays] — one bag for Detekt. */
internal data class WorkoutOverlaySheetState(
    val showRestSheet: Boolean,
    val onShowRestSheetChange: (Boolean) -> Unit,
    val showDiscardDialog: Boolean,
    val onShowDiscardDialogChange: (Boolean) -> Unit,
    val liftActionsLiftId: String?,
    val onLiftActionsLiftIdChange: (String?) -> Unit,
    val notes: LiftNotesSheetHostState,
    val configureRestLiftId: String?,
    val onConfigureRestLiftIdChange: (String?) -> Unit,
    val showRestConfigDiscardDialog: Boolean,
    val onShowRestConfigDiscardDialogChange: (Boolean) -> Unit,
    val supersetSheetLiftId: String?,
    val onSupersetSheetLiftIdChange: (String?) -> Unit,
    val showCompleteSheet: Boolean,
    val completeSheetPhase: CompleteSheetPhase,
    val onShowCompleteSheetChange: (Boolean) -> Unit,
    val onCompleteSheetPhaseChange: (CompleteSheetPhase) -> Unit,
)

/**
 * Rest / discard / lift-actions / superset / complete sheets hosted outside [WorkoutScreen]
 * so Detekt cyclomatic complexity stays under the threshold.
 */
@Composable
internal fun WorkoutSessionOverlays(
    state: WorkoutUiState.Loaded,
    restTimer: RestTimerChrome,
    actions: WorkoutScreenActions,
    sheets: WorkoutOverlaySheetState,
) {
    if (sheets.showRestSheet) {
        RestTimerBottomSheet(
            isTimerActive = restTimer.isActive,
            remainingSeconds = restTimer.remainingSeconds,
            totalSeconds = restTimer.totalSeconds,
            onDismiss = { sheets.onShowRestSheetChange(false) },
            onAdd10s = restTimer.onAdd10s,
            onSubtract10s = restTimer.onSubtract10s,
            onSkip = {
                restTimer.onSkip()
                sheets.onShowRestSheetChange(false)
            },
            onStartPreset = restTimer.onStart,
        )
    }

    if (sheets.showDiscardDialog) {
        DiscardWorkoutDialog(
            onDismiss = { sheets.onShowDiscardDialogChange(false) },
            onConfirm = {
                sheets.onShowDiscardDialogChange(false)
                // Navigate only after domain abandon succeeds (WorkoutUiEvent.NavigateBackAfterAbandon).
                actions.onAbandon()
            },
        )
    }

    LiftActionsAndRestConfigSheets(
        state = state,
        actions = actions,
        liftActionsLiftId = sheets.liftActionsLiftId,
        onLiftActionsLiftIdChange = sheets.onLiftActionsLiftIdChange,
        notesHost = sheets.notes,
        configureRestLiftId = sheets.configureRestLiftId,
        onConfigureRestLiftIdChange = sheets.onConfigureRestLiftIdChange,
        showRestConfigDiscardDialog = sheets.showRestConfigDiscardDialog,
        onShowRestConfigDiscardDialogChange = sheets.onShowRestConfigDiscardDialogChange,
        onOpenSupersetSheet = { liftId ->
            sheets.onLiftActionsLiftIdChange(null)
            sheets.onSupersetSheetLiftIdChange(liftId)
        },
    )

    sheets.supersetSheetLiftId?.let { liftId ->
        val currentLift = state.lifts.firstOrNull { it.workoutLiftId == liftId }
        val options = supersetLinkSheetOptions(lifts = state.lifts, workoutLiftId = liftId)
        if (options.isNotEmpty() && currentLift != null) {
            SupersetLinkBottomSheet(
                options = options,
                showRestBetweenLiftsToggle =
                    isSupersetPairMember(currentLift, state.lifts) &&
                        !isLastInSupersetChain(currentLift, state.lifts),
                useRestTimerBetweenLifts = currentLift.useRestTimerBetweenLifts,
                onUseRestTimerBetweenLiftsChange = { enabled ->
                    actions.onSetUseRestTimerBetweenLifts(liftId, enabled)
                },
                onOptionSelected = { direction ->
                    when (direction) {
                        SupersetLinkDirection.Previous -> actions.onLinkSupersetWithPrevious(liftId)
                        SupersetLinkDirection.Next -> actions.onLinkSupersetWithNext(liftId)
                        SupersetLinkDirection.Remove -> actions.onRemoveFromSuperset(liftId)
                    }
                    if (!shouldKeepSupersetSheetOpenAfterLink(state.lifts, liftId, direction)) {
                        sheets.onSupersetSheetLiftIdChange(null)
                    }
                },
                onDismiss = { sheets.onSupersetSheetLiftIdChange(null) },
            )
        } else {
            sheets.onSupersetSheetLiftIdChange(null)
        }
    }

    if (sheets.showCompleteSheet) {
        CompleteWorkoutSheet(
            phase = sheets.completeSheetPhase,
            onContinueWorkout = { sheets.onShowCompleteSheetChange(false) },
            onCompleteWorkout = {
                val shouldAskSaveTemplate =
                    state.hasDirtyEdits &&
                        sheets.completeSheetPhase != CompleteSheetPhase.SaveChanges
                if (shouldAskSaveTemplate) {
                    sheets.onCompleteSheetPhaseChange(CompleteSheetPhase.SaveChanges)
                } else {
                    sheets.onShowCompleteSheetChange(false)
                    actions.onCompleteWorkout(false)
                }
            },
            onUpdateTemplate = {
                sheets.onShowCompleteSheetChange(false)
                actions.onCompleteWorkout(true)
            },
            onDiscardChanges = {
                sheets.onShowCompleteSheetChange(false)
                actions.onCompleteWorkout(false)
            },
            onDismiss = { sheets.onShowCompleteSheetChange(false) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestTimerBottomSheet(
    isTimerActive: Boolean,
    remainingSeconds: Int,
    totalSeconds: Int,
    onDismiss: () -> Unit,
    onAdd10s: () -> Unit,
    onSubtract10s: () -> Unit,
    onSkip: () -> Unit,
    onStartPreset: (Int) -> Unit,
) {
    StratumBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        StratumText(
            text = "Rest Timer",
            style = StratumThemeAccessor.typography.titleLarge,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 8.dp)
        )

        val progress = if (totalSeconds > 0) remainingSeconds.toFloat() / totalSeconds else 0f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(StratumThemeAccessor.colors.surfaceSunken)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(StratumThemeAccessor.colors.accent)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isTimerActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StratumIconButton(onClick = onSubtract10s) {
                    Icon(
                        painter = painterResource(R.drawable.back_10),
                        contentDescription = "Reduce 10 seconds",
                        tint = StratumThemeAccessor.colors.textPrimary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = StratumThemeAccessor.colors.textPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                    StratumText(
                        text = formatRestDisplay(remainingSeconds),
                        style = StratumThemeAccessor.typography.titleLarge.copy(
                            fontSize = StratumThemeAccessor.typography.titleLarge.fontSize * 1.5
                        )
                    )
                }

                StratumIconButton(onClick = onAdd10s) {
                    Icon(
                        painter = painterResource(R.drawable.forward_10),
                        contentDescription = "Add 10 seconds",
                        tint = StratumThemeAccessor.colors.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            StratumButton(
                text = "Skip",
                onClick = onSkip,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        } else {
            StratumText(
                text = "Choose Rest Time to Start Timer:",
                style = StratumThemeAccessor.typography.bodyLarge,
                muted = true,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 8.dp)
            )

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val presets = (30..RestTimerDefaults.MAX_REST_SECONDS step 30).toList()
                presets.forEach { seconds ->
                    val min = seconds / 60
                    val sec = seconds % 60
                    val label = if (sec == 0) "$min:00" else "$min:$sec"
                    Box(
                        modifier = Modifier
                            .clip(StratumThemeAccessor.shapes.chip)
                            .background(StratumThemeAccessor.colors.surfaceInset)
                            .border(
                                width = StratumThemeAccessor.shapes.borderWidth,
                                color = StratumThemeAccessor.colors.borderSubtle,
                                shape = StratumThemeAccessor.shapes.chip
                            )
                            .clickable { onStartPreset(seconds) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        StratumText(
                            text = label,
                            style = StratumThemeAccessor.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscardWorkoutDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StratumText(
                text = stringResource(R.string.workout_screen_discard_workout),
                style = StratumThemeAccessor.typography.titleLarge
            )
            StratumText(
                text = stringResource(R.string.workout_screen_discard_confirm_body),
                style = StratumThemeAccessor.typography.bodyLarge,
                muted = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                StratumOutlinedButton(
                    text = stringResource(R.string.workout_screen_cancel_discard),
                    onClick = onDismiss,
                )
                StratumButton(
                    text = stringResource(R.string.workout_screen_confirm_discard),
                    onClick = onConfirm,
                )
            }
        }
    }
}

internal fun formatRestDisplay(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    val sStr = s.toString().padStart(2, '0')
    return "$m:$sStr"
}

internal fun hasUnsavedRestChanges(
    baseline: Map<String, Int>,
    pending: Map<String, Int>,
): Boolean =
    pending.size != baseline.size ||
        pending.any { (key, value) -> baseline[key] != value }


/**
 * Hosts Lift Options + Configure Rest Times sheets so [WorkoutScreen] stays under
 * Detekt cyclomatic-complexity limits.
 */
@Suppress("LongParameterList")
@Composable
private fun LiftActionsAndRestConfigSheets(
    state: WorkoutUiState.Loaded,
    actions: WorkoutScreenActions,
    liftActionsLiftId: String?,
    onLiftActionsLiftIdChange: (String?) -> Unit,
    notesHost: LiftNotesSheetHostState,
    configureRestLiftId: String?,
    onConfigureRestLiftIdChange: (String?) -> Unit,
    showRestConfigDiscardDialog: Boolean,
    onShowRestConfigDiscardDialogChange: (Boolean) -> Unit,
    onOpenSupersetSheet: (String) -> Unit,
) {
    liftActionsLiftId?.let { liftId ->
        val lift = state.lifts.firstOrNull { it.workoutLiftId == liftId }
        if (lift != null) {
            StratumBottomSheet(
                onDismissRequest = { onLiftActionsLiftIdChange(null) },
                title =
                    if (notesHost.liftSheetMode is LiftOptionsSheetMode.Options) {
                        stringResource(CoreUiR.string.edit_workout_lift_menu)
                    } else {
                        null
                    },
            ) {
                when (val mode = notesHost.liftSheetMode) {
                    LiftOptionsSheetMode.Options ->
                        LiftActionsSheetContent(
                            onConfigureSuperset = { onOpenSupersetSheet(liftId) },
                            onConfigureRestTimes = {
                                onLiftActionsLiftIdChange(null)
                                onConfigureRestLiftIdChange(liftId)
                            },
                            onManageNotes = {
                                notesHost.onLiftSheetModeChange(LiftOptionsSheetMode.ManageNotes(LiftNoteTab.Session))
                            },
                            onViewPastSessionNotes = {
                                onLiftActionsLiftIdChange(null)
                                notesHost.onPastSessionNotesLiftIdChange(liftId)
                            },
                            onSwapLift = {
                                onLiftActionsLiftIdChange(null)
                                actions.onSwapLiftClick(state.workoutId, liftId, lift.liftId, lift.liftName)
                            },
                            onEditEquipment = {
                                onLiftActionsLiftIdChange(null)
                                actions.onEditEquipmentClick(
                                    EquipmentEditRequest(
                                        liftId = lift.liftId,
                                        target =
                                            EquipmentEditTarget(
                                                equipmentId = lift.equipmentId,
                                            ),
                                    ),
                                )
                            },
                            onRemoveLift = {
                                onLiftActionsLiftIdChange(null)
                                actions.onRemoveLift(liftId)
                            },
                            onReorderLifts = {
                                onLiftActionsLiftIdChange(null)
                                actions.onReorderLiftsClick()
                            },
                        )
                    is LiftOptionsSheetMode.ManageNotes ->
                        ManageNotesSummaryContent(
                            lift = lift,
                            initialTab = mode.tab,
                            loadNoteLists = actions.liftNotes.onLoadNoteLists,
                            onEditNotes = { tab ->
                                onLiftActionsLiftIdChange(null)
                                notesHost.onEditNotes(
                                    EditingNotesTarget(
                                        workoutLiftId = lift.workoutLiftId,
                                        liftId = lift.liftId,
                                        liftName = lift.liftName,
                                        tab = tab,
                                    ),
                                )
                            },
                            onDismiss = { onLiftActionsLiftIdChange(null) },
                        )
                }
            }
        } else {
            onLiftActionsLiftIdChange(null)
        }
    }

    notesHost.pastSessionNotesLiftId?.let { liftId ->
        val lift = state.lifts.firstOrNull { it.workoutLiftId == liftId }
        if (lift != null) {
            var notes by remember(lift.liftId) { mutableStateOf<List<PastSessionNotesGroupUi>>(emptyList()) }
            LaunchedEffect(lift.liftId) {
                notes = actions.liftNotes.onLoadPastNotes(lift.liftId)
            }
            PastSessionNotesSheet(
                notes = notes,
                onDismiss = { notesHost.onPastSessionNotesLiftIdChange(null) },
            )
        } else {
            notesHost.onPastSessionNotesLiftIdChange(null)
        }
    }

    configureRestLiftId?.let { liftId ->
        val lift = state.lifts.firstOrNull { it.workoutLiftId == liftId }
        if (lift != null) {
            ConfigureRestTimesBottomSheet(
                lift = lift,
                onConfirm = { updates ->
                    if (updates.isNotEmpty()) {
                        actions.onSetRestSecondsForSlots(updates)
                    }
                    onConfigureRestLiftIdChange(null)
                    onShowRestConfigDiscardDialogChange(false)
                },
                onRequestDismiss = { hasUnsaved ->
                    if (hasUnsaved) {
                        onShowRestConfigDiscardDialogChange(true)
                    } else {
                        onConfigureRestLiftIdChange(null)
                    }
                },
            )
            if (showRestConfigDiscardDialog) {
                RestConfigDiscardBottomSheet(
                    onConfirmDiscard = {
                        onShowRestConfigDiscardDialogChange(false)
                        onConfigureRestLiftIdChange(null)
                    },
                    onDismiss = { onShowRestConfigDiscardDialogChange(false) },
                )
            }
        } else {
            onConfigureRestLiftIdChange(null)
            onShowRestConfigDiscardDialogChange(false)
        }
    }
}

/**
 * Nested Configure Rest Times mode (UX option a): list each set with a tappable rest
 * field; expanding a row shows [StratumDurationStepper] + confirm. Dismiss with unsaved
 * edits routes through [onRequestDismiss] so the host can show a discard dialog.
 */
@Composable
private fun ConfigureRestTimesBottomSheet(
    lift: RenderedLiftUi,
    onConfirm: (Map<String, Int>) -> Unit,
    onRequestDismiss: (hasUnsaved: Boolean) -> Unit,
) {
    val baseline = remember(lift.workoutLiftId, lift.sets.map { it.slotKey to it.restSeconds }) {
        lift.sets.associate { it.slotKey to it.restSeconds }
    }
    val pending = remember(lift.workoutLiftId) {
        mutableStateMapOf<String, Int>().apply { putAll(baseline) }
    }
    var expandedSlotKey by remember(lift.workoutLiftId) { mutableStateOf<String?>(null) }
    val hasUnsaved = hasUnsavedRestChanges(baseline = baseline, pending = pending)

    StratumBottomSheet(
        onDismissRequest = { onRequestDismiss(hasUnsaved) },
        title = stringResource(CoreUiR.string.edit_workout_configure_rest_times),
    ) {
        lift.sets.forEach { set ->
            val restSeconds = pending[set.slotKey] ?: set.restSeconds
            val isExpanded = expandedSlotKey == set.slotKey
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
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedSlotKey = if (isExpanded) null else set.slotKey
                            },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StratumText(
                        text = stringResource(
                            if (set.setKind.isMyoRepKind()) {
                                CoreUiR.string.edit_workout_rest_mini_set_label
                            } else {
                                CoreUiR.string.edit_workout_rest_set_label
                            },
                            set.positionLabel.toIntOrNull() ?: (lift.sets.indexOf(set) + 1),
                        ),
                        style = StratumThemeAccessor.typography.bodyLarge,
                    )
                    StratumText(
                        text = formatRestDisplay(restSeconds),
                        style = StratumThemeAccessor.typography.titleMedium,
                    )
                }
                if (isExpanded) {
                    StratumDurationStepper(
                        totalMinutes = restSeconds,
                        onTotalMinutesChange = { pending[set.slotKey] = it },
                        minMinutes = MIN_CONFIGURE_REST_SECONDS,
                        maxMinutes = MAX_CONFIGURE_REST_SECONDS,
                        minutesStep = CONFIGURE_REST_SECONDS_STEP,
                        hoursLabel = stringResource(CoreUiR.string.edit_workout_rest_minutes_label),
                        minutesLabel = stringResource(CoreUiR.string.edit_workout_rest_seconds_label),
                        incrementContentDescription =
                            stringResource(CoreUiR.string.edit_workout_rest_increment),
                        decrementContentDescription =
                            stringResource(CoreUiR.string.edit_workout_rest_decrement),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        StratumButton(
            text = stringResource(CoreUiR.string.edit_workout_rest_confirm),
            onClick = {
                val updates =
                    pending
                        .filter { (key, value) -> baseline[key] != value }
                        .toMap()
                onConfirm(updates)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RestConfigDiscardBottomSheet(
    onConfirmDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    StratumBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(CoreUiR.string.edit_workout_rest_discard_title),
    ) {
        StratumText(
            text = stringResource(CoreUiR.string.edit_workout_rest_discard_message),
            style = StratumThemeAccessor.typography.bodyLarge,
        )
        StratumButton(
            text = stringResource(CoreUiR.string.edit_workout_rest_discard_confirm),
            onClick = onConfirmDiscard,
            modifier = Modifier.fillMaxWidth(),
        )
        StratumOutlinedButton(
            text = stringResource(CoreUiR.string.edit_workout_rest_discard_cancel),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

enum class CompleteSheetPhase { Incomplete, SaveChanges, AllSetsLogged }

@Composable
private fun CompleteWorkoutSheet(
    phase: CompleteSheetPhase,
    onContinueWorkout: () -> Unit,
    onCompleteWorkout: () -> Unit,
    onUpdateTemplate: () -> Unit,
    onDiscardChanges: () -> Unit,
    onDismiss: () -> Unit,
) {
    StratumBottomSheet(onDismissRequest = onDismiss) {
        when (phase) {
            CompleteSheetPhase.Incomplete -> {
                StratumText(
                    text = stringResource(R.string.workout_sheet_incomplete_title),
                    style = StratumThemeAccessor.typography.titleLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                StratumText(
                    text = stringResource(R.string.workout_sheet_incomplete_message),
                    style = StratumThemeAccessor.typography.bodyLarge,
                    muted = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                StratumButton(
                    text = stringResource(R.string.workout_sheet_continue_workout),
                    onClick = onContinueWorkout,
                    modifier = Modifier.fillMaxWidth(),
                )
                StratumOutlinedButton(
                    text = stringResource(R.string.workout_sheet_complete_workout),
                    onClick = onCompleteWorkout,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            CompleteSheetPhase.AllSetsLogged -> {
                StratumText(
                    text = stringResource(R.string.workout_sheet_all_sets_done_title),
                    style = StratumThemeAccessor.typography.titleLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                StratumText(
                    text = stringResource(R.string.workout_sheet_all_sets_done_message),
                    style = StratumThemeAccessor.typography.bodyLarge,
                    muted = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Primary filled CTA spanning sheet content width (sheet already pads 12.dp).
                StratumButton(
                    text = stringResource(R.string.workout_sheet_complete_workout),
                    onClick = onCompleteWorkout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                )
            }
            CompleteSheetPhase.SaveChanges -> {
                StratumText(
                    text = stringResource(R.string.workout_sheet_save_changes_title),
                    style = StratumThemeAccessor.typography.titleLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                StratumText(
                    text = stringResource(R.string.workout_sheet_save_changes_message),
                    style = StratumThemeAccessor.typography.bodyLarge,
                    muted = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                StratumButton(
                    text = stringResource(R.string.workout_sheet_update_template),
                    onClick = onUpdateTemplate,
                    modifier = Modifier.fillMaxWidth(),
                )
                StratumOutlinedButton(
                    text = stringResource(R.string.workout_sheet_discard_changes),
                    onClick = onDiscardChanges,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}


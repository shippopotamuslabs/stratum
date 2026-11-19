@file:Suppress("MatchingDeclarationName")

package com.stratum.feature.program.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import com.stratum.core.ui.component.LiftActionsBottomSheet
import com.stratum.core.ui.component.RirTargetSheetContent
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.workoutlog.validateRepRangeDrafts
import com.stratum.domain.program.ProgressionScheme
import com.stratum.feature.program.CreateProgramDayUi
import com.stratum.feature.program.ProgramSheetLiftStep
import com.stratum.feature.program.ProgramSheetLiftTarget
import com.stratum.feature.program.ProgramSheetRow
import com.stratum.feature.program.R
import com.stratum.feature.program.isEditMatchBackoff
import com.stratum.feature.program.isMiniRestKind
import com.stratum.feature.program.programSheetAdjacentLift
import com.stratum.feature.program.showsRepRange

/** Collapse control for one expanded sheet card. Null keeps the tabbed editor card unchanged. */
internal val LocalEditLiftCollapse = staticCompositionLocalOf<(() -> Unit)?> { null }

internal data class ProgramSheetLiftMove(
    val target: ProgramSheetLiftTarget,
    val rirSetId: String?,
)

/**
 * Reveal the open dock cell after the lift scroll has finished.
 * A closed dock stays quiet so a later open is not a stale reveal.
 * An immediate reveal from retarget cancels the lift scroll: both use the
 * default scroll priority, and an equal priority interrupts the one in progress.
 */
internal fun revealSheetDockAfterLiftScroll(host: EditWorkoutDockHostState) {
    if (host.isOpen) host.overlay.requestReveal()
}

/**
 * Before a close that is followed by a programmatic lift scroll, skip the dock's
 * collapse scroll-restore so it does not cancel that scroll. A blocked leave that
 * keeps the dock open clears the flag; a closed dock leaves it false.
 *
 * @return whatever [leave] returns.
 */
internal fun skipDockRestoreForProgramSheetLiftScroll(
    host: EditWorkoutDockHostState,
    leave: () -> Boolean,
): Boolean {
    host.overlay.skipNextCollapseScrollRestore = host.isOpen
    val left = leave()
    if (!left && host.isOpen) {
        host.overlay.skipNextCollapseScrollRestore = false
    }
    return left
}

/**
 * Set an open rep pad can retarget onto.
 * Prefers [preferredIndex], then the nearest eligible set in [step]'s direction,
 * then the nearest the other way. Hidden cells and bands the pad would reject
 * (including stored `0/0`) are skipped. Null when none qualify.
 */
internal fun programSheetRepPadTarget(
    sets: List<EditSetState>,
    scheme: ProgressionScheme,
    preferredIndex: Int,
    step: ProgramSheetLiftStep,
): EditSetState? {
    if (preferredIndex !in sets.indices) return null
    val primaryDirection = if (step == ProgramSheetLiftStep.Next) 1 else -1
    return listOf(0, primaryDirection, -primaryDirection)
        .flatMap { direction -> repPadCandidateIndices(sets.lastIndex, preferredIndex, direction) }
        .firstNotNullOfOrNull { index ->
            sets[index].takeIf { isProgramSheetRepPadSet(sets, scheme, index) }
        }
}

/**
 * Walks to the adjacent sheet lift, selects that day, and retargets an open
 * rep, rest, or RIR editor. Rep pads land on an eligible band, or close when
 * the destination has none. Does not commit; the caller runs
 * [EditWorkoutDockHostState.tryCommitPendingAndStay] first.
 */
internal fun movedProgramSheetLift(
    expanded: ProgramSheetLiftTarget?,
    rows: List<ProgramSheetRow>,
    days: List<CreateProgramDayUi>,
    step: ProgramSheetLiftStep,
    anchorSetId: String?,
    host: EditWorkoutDockHostState,
    onSelectDay: (String) -> Unit,
    onSetRepBand: (setId: String, repMin: Int, repMax: Int) -> Unit,
    onSetRestSeconds: (setId: String, restSeconds: Int) -> Unit,
    onSetRestSecondsForAllSetsOnLift: (workoutLiftId: String, restSeconds: Int) -> Unit,
    rirSetId: String?,
): ProgramSheetLiftMove? {
    val current = expanded ?: return null
    val destination =
        programSheetAdjacentLift(
            rows = rows,
            dayId = current.dayId,
            workoutLiftId = current.workoutLiftId,
            step = step,
        ) ?: return null
    val destinationLift = findSheetLift(days, destination) ?: return null
    onSelectDay(destination.dayId)
    if (destinationLift.sets.isEmpty()) {
        closeProgramSheetDockForLiftScroll(host)
        return ProgramSheetLiftMove(target = destination, rirSetId = null)
    }
    val sourceLift = findSheetLift(days, current)
    val sourceIndex = sourceLift?.sets?.indexOfFirst { set -> set.setId == anchorSetId } ?: -1
    val resolvedIndex = (if (sourceIndex >= 0) sourceIndex else 0).coerceIn(0, destinationLift.sets.lastIndex)
    val destinationSet = destinationLift.sets[resolvedIndex]
    when (host.session) {
        is EditWorkoutDockSession.RepRange ->
            retargetProgramSheetRepPad(
                host = host,
                destinationLift = destinationLift,
                preferredIndex = resolvedIndex,
                step = step,
                dayId = destination.dayId,
                onSelectDay = onSelectDay,
                onSetRepBand = onSetRepBand,
            )
        is EditWorkoutDockSession.Rest ->
            host.retargetRest(
                setId = destinationSet.setId,
                restSeconds = destinationSet.restSeconds,
                onApplyToSet = { seconds ->
                    onSelectDay(destination.dayId)
                    onSetRestSeconds(destinationSet.setId, seconds)
                },
                onApplyToAllSets = { seconds ->
                    onSelectDay(destination.dayId)
                    onSetRestSecondsForAllSetsOnLift(destinationLift.workoutLiftId, seconds)
                },
                isMiniRest = isMiniRestKind(destinationSet.setKind),
            )
        null -> Unit
    }
    val nextRir = if (rirSetId == null) null else destinationSet.setId
    return ProgramSheetLiftMove(target = destination, rirSetId = nextRir)
}

private fun closeProgramSheetDockForLiftScroll(host: EditWorkoutDockHostState) {
    skipDockRestoreForProgramSheetLiftScroll(host) {
        host.closeWithoutCommit()
        true
    }
}

private fun retargetProgramSheetRepPad(
    host: EditWorkoutDockHostState,
    destinationLift: EditLiftState,
    preferredIndex: Int,
    step: ProgramSheetLiftStep,
    dayId: String,
    onSelectDay: (String) -> Unit,
    onSetRepBand: (setId: String, repMin: Int, repMax: Int) -> Unit,
) {
    val repSet =
        programSheetRepPadTarget(
            sets = destinationLift.sets,
            scheme = destinationLift.progressionScheme,
            preferredIndex = preferredIndex,
            step = step,
        )
    if (repSet == null) {
        closeProgramSheetDockForLiftScroll(host)
        return
    }
    host.retargetRepRange(
        setId = repSet.setId,
        minText = repSet.repMin.toString(),
        maxText = repSet.repMax.toString(),
        onCommitBand = { repMin, repMax ->
            onSelectDay(dayId)
            onSetRepBand(repSet.setId, repMin, repMax)
        },
    )
}

private fun repPadCandidateIndices(lastIndex: Int, start: Int, direction: Int): List<Int> =
    when (direction) {
        0 -> listOf(start)
        1 -> (start + 1..lastIndex).toList()
        -1 -> (start - 1 downTo 0).toList()
        else -> emptyList()
    }

private fun isProgramSheetRepPadSet(
    sets: List<EditSetState>,
    scheme: ProgressionScheme,
    index: Int,
): Boolean {
    val set = sets[index]
    val showsCell =
        showsRepRange(scheme, set) &&
            set.setKind != EditSetKind.DropSet &&
            !isEditMatchBackoff(sets, index)
    if (!showsCell) return false
    return validateRepRangeDrafts(
        minText = set.repMin.toString(),
        maxText = set.repMax.toString(),
        requireCompleteBand = true,
    ).isValid
}

@Composable
internal fun ProgramSheetExpandedLift(
    lift: EditLiftState,
    allLifts: List<EditLiftState>,
    callbacks: EditWorkoutScreenCallbacks,
    onCollapse: () -> Unit,
    onShowLiftActions: () -> Unit,
    onRirClick: (EditSetState) -> Unit,
    onShowProgressionSchemeInfo: () -> Unit,
) {
    CompositionLocalProvider(LocalEditLiftCollapse provides onCollapse) {
        EditLiftPanel(
            lift = lift,
            allLifts = allLifts,
            onAddSet = { callbacks.onAddSet(lift.workoutLiftId) },
            onRemoveSet = callbacks.onRemoveSet,
            onShowLiftActions = onShowLiftActions,
            onRirClick = onRirClick,
            onSetRepBand = callbacks.onSetRepBand,
            onSetRestSeconds = callbacks.onSetRestSeconds,
            onSetRestSecondsForAllSetsOnLift = callbacks.onSetRestSecondsForAllSetsOnLift,
            onSetProgressionScheme = callbacks.onSetProgressionScheme,
            onShowProgressionSchemeInfo = onShowProgressionSchemeInfo,
            onSetKind = callbacks.onSetSetKind,
            onSetMatchBackoffCount = callbacks.onSetMatchBackoffCount,
            onEquipmentSwapClick =
                callbacks.onEquipmentSwapClick?.let { onSwap ->
                    { onSwap(lift.workoutLiftId) }
                },
        )
    }
}

@Composable
internal fun ProgramSheetLiftSheets(
    days: List<CreateProgramDayUi>,
    callbacks: CreateProgramScreenCallbacks,
    rirSetId: String?,
    onDismissRir: () -> Unit,
    onPreviousLift: () -> Unit,
    onNextLift: () -> Unit,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    liftActionsLiftId: String?,
    onDismissLiftActions: () -> Unit,
    showProgressionSchemeExplainer: Boolean,
    onDismissProgressionSchemeExplainer: () -> Unit,
) {
    val locatedRir = rirSetId?.let { setId -> locateSheetSet(days, setId) }
    if (locatedRir != null) {
        StratumBottomSheet(onDismissRequest = onDismissRir) {
            RirTargetSheetContent(
                selectedRirGoal = locatedRir.set.rirGoal,
                onRirSelected = { rir ->
                    callbacks.onSelectDay(locatedRir.dayId)
                    callbacks.onSetRirGoal(locatedRir.set.setId, rir)
                },
                onDismiss = onDismissRir,
            )
            EditWorkoutLiftStepButtons(
                onPrevious = onPreviousLift,
                onNext = onNextLift,
                previousEnabled = previousEnabled,
                nextEnabled = nextEnabled,
            )
        }
    }

    val locatedActions = liftActionsLiftId?.let { liftId -> locateSheetLift(days, liftId) }
    val sheetSelecting = LocalProgramSheetSelection.current.active
    if (locatedActions != null) {
        val removedMessage =
            stringResource(
                R.string.create_program_sheet_removed,
                locatedActions.lift.name,
            )
        LiftActionsBottomSheet(
            onConfigureSuperset = onDismissLiftActions,
            onSwapLift = {
                onDismissLiftActions()
                callbacks.onSelectDay(locatedActions.dayId)
                callbacks.onSwapLift(
                    locatedActions.edit.workoutId,
                    locatedActions.lift.workoutLiftId,
                    locatedActions.lift.liftId,
                    locatedActions.lift.name,
                )
            },
            onRemoveLift = {
                onDismissLiftActions()
                callbacks.onRemoveSheetLift(
                    locatedActions.dayId,
                    locatedActions.lift.workoutLiftId,
                    removedMessage,
                )
            },
            onCut =
                if (sheetSelecting) {
                    null
                } else {
                    {
                        onDismissLiftActions()
                        callbacks.onCutSheetLift(
                            locatedActions.dayId,
                            locatedActions.lift.workoutLiftId,
                        )
                    }
                },
            onCopy =
                if (sheetSelecting) {
                    null
                } else {
                    {
                        onDismissLiftActions()
                        callbacks.onCopySheetLift(
                            locatedActions.dayId,
                            locatedActions.lift.workoutLiftId,
                        )
                    }
                },
            onSelect =
                if (sheetSelecting) {
                    null
                } else {
                    {
                        onDismissLiftActions()
                        callbacks.onBeginSheetSelect(
                            locatedActions.dayId,
                            locatedActions.lift.workoutLiftId,
                        )
                    }
                },
            onDismiss = onDismissLiftActions,
        )
    }

    if (showProgressionSchemeExplainer) {
        ProgressionSchemeExplainerSheet(onDismiss = onDismissProgressionSchemeExplainer)
    }
}

internal fun EditWorkoutScreenCallbacks.scopedToDay(
    dayId: String,
    onSelectDay: (String) -> Unit,
): EditWorkoutScreenCallbacks {
    val select = { onSelectDay(dayId) }
    val addSet = onAddSet
    val removeSet = onRemoveSet
    val removeLift = onRemoveLift
    val linkPrevious = onLinkSupersetWithPrevious
    val linkNext = onLinkSupersetWithNext
    val removeSuperset = onRemoveFromSuperset
    val setRestTimer = onSetUseRestTimerBetweenLifts
    val addLift = onAddLift
    val swap = onSwapLiftClick
    val reorder = onReorderLiftsClick
    val setRepBand = onSetRepBand
    val setRir = onSetRirGoal
    val setRest = onSetRestSeconds
    val setRestAll = onSetRestSecondsForAllSetsOnLift
    val setScheme = onSetProgressionScheme
    val setKind = onSetSetKind
    val setMatch = onSetMatchBackoffCount
    val swapEquipment = onEquipmentSwapClick
    return copy(
        onAddSet = { id -> select(); addSet(id) },
        onRemoveSet = { id -> select(); removeSet(id) },
        onRemoveLift = { id -> select(); removeLift(id) },
        onLinkSupersetWithPrevious = { id -> select(); linkPrevious(id) },
        onLinkSupersetWithNext = { id -> select(); linkNext(id) },
        onRemoveFromSuperset = { id -> select(); removeSuperset(id) },
        onSetUseRestTimerBetweenLifts = { id, enabled -> select(); setRestTimer(id, enabled) },
        onAddLift = { select(); addLift() },
        onSwapLiftClick = { workoutId, workoutLiftId, liftId, liftName ->
            select()
            swap(workoutId, workoutLiftId, liftId, liftName)
        },
        onReorderLiftsClick = { select(); reorder() },
        onSetRepBand = { setId, repMin, repMax -> select(); setRepBand(setId, repMin, repMax) },
        onSetRirGoal = { setId, rirGoal -> select(); setRir(setId, rirGoal) },
        onSetRestSeconds = { setId, restSeconds -> select(); setRest(setId, restSeconds) },
        onSetRestSecondsForAllSetsOnLift = { workoutLiftId, restSeconds ->
            select()
            setRestAll(workoutLiftId, restSeconds)
        },
        onSetProgressionScheme = { workoutLiftId, scheme -> select(); setScheme(workoutLiftId, scheme) },
        onSetSetKind = { setId, kind -> select(); setKind(setId, kind) },
        onSetMatchBackoffCount = { activationSetId, count -> select(); setMatch(activationSetId, count) },
        onEquipmentSwapClick =
            swapEquipment?.let { onSwap ->
                { workoutLiftId -> select(); onSwap(workoutLiftId) }
            },
    )
}

private fun findSheetLift(days: List<CreateProgramDayUi>, target: ProgramSheetLiftTarget): EditLiftState? =
    days.firstOrNull { day -> day.id == target.dayId }
        ?.editState
        ?.lifts
        ?.firstOrNull { lift -> lift.workoutLiftId == target.workoutLiftId }

private data class LocatedSheetSet(
    val dayId: String,
    val set: EditSetState,
)

private fun locateSheetSet(days: List<CreateProgramDayUi>, setId: String): LocatedSheetSet? =
    days.firstNotNullOfOrNull { day ->
        val set =
            day.editState?.lifts?.firstNotNullOfOrNull { lift ->
                lift.sets.firstOrNull { candidate -> candidate.setId == setId }
            }
        set?.let { LocatedSheetSet(dayId = day.id, set = it) }
    }

private data class LocatedSheetLift(
    val dayId: String,
    val edit: EditWorkoutUiState.Loaded,
    val lift: EditLiftState,
)

private fun locateSheetLift(days: List<CreateProgramDayUi>, workoutLiftId: String): LocatedSheetLift? =
    days.firstNotNullOfOrNull { day ->
        val edit = day.editState
        val lift = edit?.lifts?.firstOrNull { candidate -> candidate.workoutLiftId == workoutLiftId }
        if (edit == null || lift == null) null else LocatedSheetLift(dayId = day.id, edit = edit, lift = lift)
    }

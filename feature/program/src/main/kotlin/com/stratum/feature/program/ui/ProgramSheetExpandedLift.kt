@file:Suppress("MatchingDeclarationName")

package com.stratum.feature.program.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.stratum.core.ui.component.LiftActionsBottomSheet
import com.stratum.core.ui.component.RirTargetSheetContent
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.feature.program.CreateProgramDayUi
import com.stratum.feature.program.ProgramSheetLiftStep
import com.stratum.feature.program.ProgramSheetLiftTarget
import com.stratum.feature.program.ProgramSheetRow
import com.stratum.feature.program.isMiniRestKind
import com.stratum.feature.program.programSheetAdjacentLift

/** Collapse control for one expanded sheet card. Null keeps the tabbed editor card unchanged. */
internal val LocalEditLiftCollapse = staticCompositionLocalOf<(() -> Unit)?> { null }

internal data class ProgramSheetLiftMove(
    val target: ProgramSheetLiftTarget,
    val rirSetId: String?,
)

/**
 * Walks to the adjacent sheet lift, selects that day, and retargets an open
 * rep, rest, or RIR editor onto the same field. Does not commit; the caller
 * runs [EditWorkoutDockHostState.tryCommitPendingAndStay] first.
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
        host.closeWithoutCommit()
        return ProgramSheetLiftMove(target = destination, rirSetId = null)
    }
    val sourceLift = findSheetLift(days, current)
    val sourceIndex = sourceLift?.sets?.indexOfFirst { set -> set.setId == anchorSetId } ?: -1
    val resolvedIndex = (if (sourceIndex >= 0) sourceIndex else 0).coerceIn(0, destinationLift.sets.lastIndex)
    val destinationSet = destinationLift.sets[resolvedIndex]
    when (host.session) {
        is EditWorkoutDockSession.RepRange ->
            host.retargetRepRange(
                setId = destinationSet.setId,
                minText = destinationSet.repMin.toString(),
                maxText = destinationSet.repMax.toString(),
                onCommitBand = { repMin, repMax ->
                    onSelectDay(destination.dayId)
                    onSetRepBand(destinationSet.setId, repMin, repMax)
                },
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
    if (locatedActions != null) {
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
                callbacks.onSelectDay(locatedActions.dayId)
                callbacks.onRemoveLift(locatedActions.lift.workoutLiftId)
            },
            onReorderLifts = {
                onDismissLiftActions()
                callbacks.onSelectDay(locatedActions.dayId)
                callbacks.onReorderLifts()
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

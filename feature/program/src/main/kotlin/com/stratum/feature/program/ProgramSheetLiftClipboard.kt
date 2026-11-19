package com.stratum.feature.program

import com.stratum.feature.program.ui.EditLiftState
import java.util.UUID

internal enum class ProgramSheetClipboardKind { Cut, Copy }

/**
 * In-memory sheet tray. Not part of [CreateProgramUiState] and not saved.
 * [sourceLiftsBefore] is the source day's lift list from before a cut.
 * Cancel inserts [lift] into the current list and uses this snapshot only to
 * put the cut's superset id back on partners. A completed move's Undo reinserts
 * the cut lift into the source list as it was when the lift was pasted.
 */
internal data class ProgramSheetClipboard(
    val kind: ProgramSheetClipboardKind,
    val fromDayId: String,
    val lift: EditLiftState,
    val originalIndex: Int,
    val sourceLiftsBefore: List<EditLiftState>,
) {
    val leavesSuperset: Boolean
        get() = lift.supersetGroupId != null
}

/**
 * Moves [workoutLiftId] onto [toDayId] at [targetPosition].
 *
 * The moved lift keeps its id and set scheme and lands with no superset.
 * Source survivors go through [removeLiftAndCleanupSupersets].
 *
 * [detachedLift] is the cut that is already off the source day. Paste passes it
 * so this does not remove the lift again. Null runs the full remove-then-insert.
 * Rest days and missing ids return [days].
 */
internal fun moveProgramSheetLift(
    days: List<CreateProgramDayUi>,
    fromDayId: String,
    toDayId: String,
    workoutLiftId: String,
    targetPosition: Int,
    detachedLift: EditLiftState? = null,
): List<CreateProgramDayUi> {
    if (!canPasteOnto(days, toDayId)) return days
    val moving = liftReadyToMove(days, fromDayId, workoutLiftId, detachedLift) ?: return days
    val landed =
        moving.copy(
            supersetGroupId = null,
            useRestTimerBetweenLifts = false,
        )
    val afterRemoval =
        if (detachedLift != null) {
            days
        } else {
            removeMovedLift(days, fromDayId, workoutLiftId) ?: return days
        }
    return insertLift(afterRemoval, toDayId, landed, targetPosition)
}

/**
 * Copies [workoutLiftId] onto [toDayId] at [targetPosition].
 * The source day is unchanged. The copy has a new lift id, new set ids, and no superset.
 * [sourceLift] is the clipboard snapshot. When it is non-null and its id matches,
 * that object is copied even if the live row was edited or removed.
 * [fromDayId] == [toDayId] appends when [targetPosition] is the current size.
 * A rest day, a missing destination, or a missing source with no snapshot returns [days].
 */
internal fun copyProgramSheetLift(
    days: List<CreateProgramDayUi>,
    fromDayId: String,
    toDayId: String,
    workoutLiftId: String,
    targetPosition: Int,
    sourceLift: EditLiftState? = null,
    newWorkoutLiftId: () -> String = ::newWorkoutLiftId,
    newSetId: () -> String = { UUID.randomUUID().toString() },
): List<CreateProgramDayUi> {
    val toDay = days.firstOrNull { day -> day.id == toDayId } ?: return days
    if (toDay.isRest || toDay.editState == null) return days
    val source = sheetCopySource(days, fromDayId, workoutLiftId, sourceLift) ?: return days
    val copied =
        source.copy(
            workoutLiftId = newWorkoutLiftId(),
            sets = source.sets.map { set -> set.copy(setId = newSetId()) },
            supersetGroupId = null,
            useRestTimerBetweenLifts = false,
        )
    return insertLift(days, toDayId, copied, targetPosition)
}

/**
 * Puts [liftsByDayId] back on those days. Lift order, set ids, and superset ids
 * are the lists that were snapshotted. Days absent from the map stay as they are.
 */
internal fun restoreProgramSheetLiftOrder(
    days: List<CreateProgramDayUi>,
    liftsByDayId: Map<String, List<EditLiftState>>,
): List<CreateProgramDayUi> {
    if (liftsByDayId.isEmpty()) return days
    var changed = false
    val next =
        days.map { day ->
            val prior = liftsByDayId[day.id]
            val edit = day.editState
            if (prior == null || edit == null) {
                day
            } else {
                changed = true
                day.copy(editState = edit.copy(lifts = prior, isDirty = true))
            }
        }
    return if (changed) next else days
}

/**
 * Inserts [lift] at [originalIndex] into the current lifts of [fromDayId].
 * Lifts added after the cut stay. Partners that still share the cut's superset
 * in [sourceLiftsBefore] get that id and their prior rest-timer flag back,
 * unless they already have a different group. A missing day returns [days].
 */
internal fun returnCutLiftToCurrentDay(
    days: List<CreateProgramDayUi>,
    fromDayId: String,
    lift: EditLiftState,
    originalIndex: Int,
    sourceLiftsBefore: List<EditLiftState>,
): List<CreateProgramDayUi> {
    val index = days.indexOfFirst { day -> day.id == fromDayId }
    if (index < 0) return days
    val day = days[index]
    val edit = day.editState ?: return days
    val inserted =
        insertCutLiftIntoCurrent(
            current = edit.lifts,
            lift = lift,
            originalIndex = originalIndex,
            sourceLiftsBefore = sourceLiftsBefore,
        )
    val updated =
        day.copy(
            isRest = false,
            editState = edit.copy(lifts = inserted, isDirty = true),
        )
    return days.toMutableList().also { copy -> copy[index] = updated }
}

/**
 * Same insert as [returnCutLiftToCurrentDay], on a lift list.
 * [originalIndex] is coerced into the current list.
 */
internal fun insertCutLiftIntoCurrent(
    current: List<EditLiftState>,
    lift: EditLiftState,
    originalIndex: Int,
    sourceLiftsBefore: List<EditLiftState>,
): List<EditLiftState> {
    val at = originalIndex.coerceIn(0, current.size)
    val withLift = current.toMutableList().apply { add(at, lift) }
    return restoreCutSupersetPartners(withLift, lift, sourceLiftsBefore)
        .mapIndexed { index, item -> item.copy(position = index) }
}

/**
 * Puts the returned cut into a sheet-delete snapshot that was taken while the
 * lift was already off the day. A snapshot that already has the lift, or a map
 * that does not include [fromDayId], is returned unchanged. Null stays null.
 */
internal fun rebaseSheetLiftUndoForReturnedCut(
    undo: Map<String, List<EditLiftState>>?,
    fromDayId: String,
    lift: EditLiftState,
    originalIndex: Int,
    sourceLiftsBefore: List<EditLiftState>,
): Map<String, List<EditLiftState>>? {
    if (undo == null) return null
    val snapshot = undo[fromDayId] ?: return undo
    if (snapshot.any { item -> item.workoutLiftId == lift.workoutLiftId }) return undo
    val rebased =
        insertCutLiftIntoCurrent(
            current = snapshot,
            lift = lift,
            originalIndex = originalIndex,
            sourceLiftsBefore = sourceLiftsBefore,
        )
    return undo + (fromDayId to rebased)
}

/**
 * Undo lists for a sheet paste. Copy only snapshots [toDayId].
 * Cut reinserts [ProgramSheetClipboard.lift] into [liveSourceLifts] (the source
 * day at paste time), so an edit made after the cut stays on that day.
 */
internal fun sheetPasteUndo(
    clip: ProgramSheetClipboard,
    toDayId: String,
    targetLifts: List<EditLiftState>,
    liveSourceLifts: List<EditLiftState>,
): Map<String, List<EditLiftState>> =
    when (clip.kind) {
        ProgramSheetClipboardKind.Cut -> {
            val sourceRestored =
                insertCutLiftIntoCurrent(
                    current = liveSourceLifts,
                    lift = clip.lift,
                    originalIndex = clip.originalIndex,
                    sourceLiftsBefore = clip.sourceLiftsBefore,
                )
            if (clip.fromDayId == toDayId) {
                mapOf(toDayId to sourceRestored)
            } else {
                mapOf(clip.fromDayId to sourceRestored, toDayId to targetLifts)
            }
        }
        ProgramSheetClipboardKind.Copy -> mapOf(toDayId to targetLifts)
    }

/**
 * Sheet undo while a save is writing is refused. The snapshot stays put.
 * An idle undo returns the restored day list, or null when there is nothing to put back.
 */
internal fun consumeSheetUndo(
    isSaving: Boolean,
    snapshot: Map<String, List<EditLiftState>>?,
    days: List<CreateProgramDayUi>,
): SheetUndoConsumption {
    if (isSaving) return SheetUndoConsumption(consumed = false, days = null)
    if (snapshot.isNullOrEmpty()) return SheetUndoConsumption(consumed = true, days = null)
    return SheetUndoConsumption(
        consumed = true,
        days = restoreProgramSheetLiftOrder(days, snapshot),
    )
}

internal data class SheetUndoConsumption(
    val consumed: Boolean,
    val days: List<CreateProgramDayUi>?,
)

/** Next snackbar identity. The same [message] still advances [currentEpoch]. */
internal fun publishSheetUndoMessage(currentEpoch: Int, message: String): SheetUndoPublish =
    SheetUndoPublish(epoch = currentEpoch + 1, message = message)

internal data class SheetUndoPublish(
    val epoch: Int,
    val message: String,
)

private fun canPasteOnto(days: List<CreateProgramDayUi>, toDayId: String): Boolean {
    val toDay = days.firstOrNull { day -> day.id == toDayId } ?: return false
    return !toDay.isRest && toDay.editState != null
}

private fun liftReadyToMove(
    days: List<CreateProgramDayUi>,
    fromDayId: String,
    workoutLiftId: String,
    detachedLift: EditLiftState?,
): EditLiftState? =
    when {
        detachedLift == null ->
            days
                .firstOrNull { day -> day.id == fromDayId }
                ?.editState
                ?.lifts
                ?.firstOrNull { lift -> lift.workoutLiftId == workoutLiftId }
        detachedLift.workoutLiftId == workoutLiftId -> detachedLift
        else -> null
    }

private fun removeMovedLift(
    days: List<CreateProgramDayUi>,
    fromDayId: String,
    workoutLiftId: String,
): List<CreateProgramDayUi>? {
    val fromIndex = days.indexOfFirst { day -> day.id == fromDayId }
    if (fromIndex < 0) return null
    val fromDay = days[fromIndex]
    val fromEdit = fromDay.editState ?: return null
    if (fromEdit.lifts.none { lift -> lift.workoutLiftId == workoutLiftId }) return null
    val removed =
        fromDay.copy(
            editState =
                fromEdit.copy(
                    lifts = removeLiftAndCleanupSupersets(fromEdit.lifts, workoutLiftId),
                    isDirty = true,
                ),
        )
    return days.toMutableList().also { copy -> copy[fromIndex] = removed }
}

private fun insertLift(
    days: List<CreateProgramDayUi>,
    toDayId: String,
    lift: EditLiftState,
    targetPosition: Int,
): List<CreateProgramDayUi> {
    val toDay = days.firstOrNull { day -> day.id == toDayId } ?: return days
    val edit = toDay.editState ?: return days
    if (toDay.isRest) return days
    val at = targetPosition.coerceIn(0, edit.lifts.size)
    val inserted =
        edit.lifts
            .toMutableList()
            .apply { add(at, lift) }
            .mapIndexed { index, item -> item.copy(position = index) }
    return days.map { day ->
        if (day.id != toDayId) {
            day
        } else {
            day.copy(
                isRest = false,
                editState = edit.copy(lifts = inserted, isDirty = true),
            )
        }
    }
}

private fun sheetCopySource(
    days: List<CreateProgramDayUi>,
    fromDayId: String,
    workoutLiftId: String,
    sourceLift: EditLiftState?,
): EditLiftState? =
    if (sourceLift != null && sourceLift.workoutLiftId == workoutLiftId) {
        sourceLift
    } else {
        days
            .firstOrNull { day -> day.id == fromDayId }
            ?.editState
            ?.lifts
            ?.firstOrNull { lift -> lift.workoutLiftId == workoutLiftId }
    }

private fun restoreCutSupersetPartners(
    lifts: List<EditLiftState>,
    cut: EditLiftState,
    sourceLiftsBefore: List<EditLiftState>,
): List<EditLiftState> {
    val groupId = cut.supersetGroupId ?: return lifts
    val priorById = sourceLiftsBefore.associateBy { partner -> partner.workoutLiftId }
    return lifts.map { item ->
        val prior = priorById[item.workoutLiftId]
        val leaveAlone =
            item.workoutLiftId == cut.workoutLiftId ||
                prior == null ||
                prior.supersetGroupId != groupId ||
                (item.supersetGroupId != null && item.supersetGroupId != groupId)
        if (leaveAlone) {
            item
        } else {
            item.copy(
                supersetGroupId = groupId,
                useRestTimerBetweenLifts = prior.useRestTimerBetweenLifts,
            )
        }
    }
}

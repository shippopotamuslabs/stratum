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
 * Applies [undo] to the live days: each step runs against its day's current lift
 * list, so edits made after the undone change survive. Days the undo does not
 * name, and days with no edit state, stay as they are.
 */
internal fun applySheetUndo(
    days: List<CreateProgramDayUi>,
    undo: ProgramSheetUndo,
): List<CreateProgramDayUi> {
    if (undo.stepsByDayId.isEmpty()) return days
    var changed = false
    val next =
        days.map { day ->
            val steps = undo.stepsByDayId[day.id]
            val edit = day.editState
            if (steps == null || edit == null) {
                day
            } else {
                changed = true
                val lifts = steps.fold(edit.lifts) { current, step -> step.applyTo(current) }
                day.copy(editState = edit.copy(lifts = lifts, isDirty = true))
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
 * One lift-list change inside a [ProgramSheetUndo]. A step runs against the
 * day's lift list as it stands when Undo is tapped, never against a snapshot,
 * so an edit made after the change being undone stays in place.
 */
internal sealed interface SheetLiftUndoStep {
    fun applyTo(lifts: List<EditLiftState>): List<EditLiftState>

    /** Takes a pasted lift back off the day it landed on. */
    data class RemoveLift(val workoutLiftId: String) : SheetLiftUndoStep {
        override fun applyTo(lifts: List<EditLiftState>): List<EditLiftState> =
            removeLiftAndCleanupSupersets(lifts, workoutLiftId)
    }

    /**
     * Puts a cut or deleted lift back into the day's current list at its old
     * index. [sourceLiftsBefore] is the day's list from before the lift left,
     * used only to restore its superset partners.
     */
    data class InsertLift(
        val lift: EditLiftState,
        val originalIndex: Int,
        val sourceLiftsBefore: List<EditLiftState>,
    ) : SheetLiftUndoStep {
        override fun applyTo(lifts: List<EditLiftState>): List<EditLiftState> =
            if (lifts.any { item -> item.workoutLiftId == lift.workoutLiftId }) {
                lifts
            } else {
                insertCutLiftIntoCurrent(
                    current = lifts,
                    lift = lift,
                    originalIndex = originalIndex,
                    sourceLiftsBefore = sourceLiftsBefore,
                )
            }
    }
}

/** A pending sheet Undo: per-day steps, applied to the live days on tap. */
internal data class ProgramSheetUndo(
    val stepsByDayId: Map<String, List<SheetLiftUndoStep>>,
) {
    val isEmpty: Boolean
        get() = stepsByDayId.isEmpty()
}

/**
 * Undo for a sheet paste. A cut is removed from the day it landed on and put
 * back into its source day's current list; a copy is only removed from the
 * day it landed on. [pastedWorkoutLiftId] is the id the paste landed with:
 * the cut lift's own id, or the copy's new id.
 */
internal fun sheetPasteUndo(
    clip: ProgramSheetClipboard,
    toDayId: String,
    pastedWorkoutLiftId: String,
): ProgramSheetUndo =
    when (clip.kind) {
        ProgramSheetClipboardKind.Cut -> {
            val insert =
                SheetLiftUndoStep.InsertLift(
                    lift = clip.lift,
                    originalIndex = clip.originalIndex,
                    sourceLiftsBefore = clip.sourceLiftsBefore,
                )
            val remove = SheetLiftUndoStep.RemoveLift(pastedWorkoutLiftId)
            if (clip.fromDayId == toDayId) {
                ProgramSheetUndo(mapOf(toDayId to listOf(remove, insert)))
            } else {
                ProgramSheetUndo(
                    mapOf(
                        clip.fromDayId to listOf(insert),
                        toDayId to listOf(remove),
                    ),
                )
            }
        }
        ProgramSheetClipboardKind.Copy ->
            ProgramSheetUndo(mapOf(toDayId to listOf(SheetLiftUndoStep.RemoveLift(pastedWorkoutLiftId))))
    }

/**
 * Realigns a pending insert step on [fromDayId] with a cut lift that just came
 * back to that day. The step's index was counted while the cut lift was off
 * the day; without the shift the undone lift lands one slot early. The step's
 * lift also takes back the superset group and rest-timer flag the cut had
 * dissolved, which the day's own before-list no longer remembers. Steps whose
 * before-list already contains the returned lift, and a step for the returned
 * lift itself, are left alone. Null stays null.
 */
internal fun rebaseSheetUndoForReturnedCut(
    undo: ProgramSheetUndo?,
    fromDayId: String,
    lift: EditLiftState,
    originalIndex: Int,
    sourceLiftsBefore: List<EditLiftState>,
): ProgramSheetUndo? {
    if (undo == null) return null
    val steps = undo.stepsByDayId[fromDayId] ?: return undo
    val realigned =
        steps.map { step ->
            val insert = step as? SheetLiftUndoStep.InsertLift
            if (
                insert == null ||
                    insert.lift.workoutLiftId == lift.workoutLiftId ||
                    insert.sourceLiftsBefore.any { item -> item.workoutLiftId == lift.workoutLiftId }
            ) {
                step
            } else {
                val precut =
                    sourceLiftsBefore.firstOrNull { item -> item.workoutLiftId == insert.lift.workoutLiftId }
                if (precut == null) {
                    step
                } else {
                    val relifted =
                        insert.copy(
                            lift =
                                insert.lift.copy(
                                    supersetGroupId = precut.supersetGroupId,
                                    useRestTimerBetweenLifts = precut.useRestTimerBetweenLifts,
                                ),
                        )
                    if (
                        sourceLiftsBefore.indexOfFirst { item -> item.workoutLiftId == insert.lift.workoutLiftId } >
                            originalIndex
                    ) {
                        relifted.copy(originalIndex = relifted.originalIndex + 1)
                    } else {
                        relifted
                    }
                }
            }
        }
    if (realigned == steps) return undo
    return undo.copy(stepsByDayId = undo.stepsByDayId + (fromDayId to realigned))
}

/** Undo for a sheet delete: the lift goes back into its day's current list at its old index. */
internal fun sheetDeleteUndo(
    dayId: String,
    lift: EditLiftState,
    originalIndex: Int,
    liftsBefore: List<EditLiftState>,
): ProgramSheetUndo =
    ProgramSheetUndo(
        mapOf(
            dayId to
                listOf(
                    SheetLiftUndoStep.InsertLift(
                        lift = lift,
                        originalIndex = originalIndex,
                        sourceLiftsBefore = liftsBefore,
                    ),
                ),
        ),
    )

/**
 * Sheet undo while a save is writing is refused. The undo stays put.
 * An idle undo returns the restored day list, or null when there is nothing to put back.
 */
internal fun consumeSheetUndo(
    isSaving: Boolean,
    undo: ProgramSheetUndo?,
    days: List<CreateProgramDayUi>,
): SheetUndoConsumption {
    if (isSaving) return SheetUndoConsumption(consumed = false, days = null)
    if (undo == null || undo.isEmpty) return SheetUndoConsumption(consumed = true, days = null)
    return SheetUndoConsumption(
        consumed = true,
        days = applySheetUndo(days, undo),
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

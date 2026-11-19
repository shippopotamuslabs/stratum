package com.stratum.feature.program

import com.stratum.feature.program.ui.EditLiftState
import java.util.UUID

internal enum class ProgramSheetClipboardKind { Cut, Copy }

/**
 * In-memory sheet tray. Not part of [CreateProgramUiState] and not saved.
 * [sourceLiftsBefore] is the source day's lift list from before a cut, so Cancel
 * can put that day back, including superset ids.
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
 * [fromDayId] == [toDayId] appends when [targetPosition] is the current size.
 * Rest days and missing ids return [days].
 */
internal fun copyProgramSheetLift(
    days: List<CreateProgramDayUi>,
    fromDayId: String,
    toDayId: String,
    workoutLiftId: String,
    targetPosition: Int,
    newWorkoutLiftId: () -> String = ::newWorkoutLiftId,
    newSetId: () -> String = { UUID.randomUUID().toString() },
): List<CreateProgramDayUi> {
    val toDay = days.firstOrNull { day -> day.id == toDayId } ?: return days
    if (toDay.isRest || toDay.editState == null) return days
    val source =
        days
            .firstOrNull { day -> day.id == fromDayId }
            ?.editState
            ?.lifts
            ?.firstOrNull { lift -> lift.workoutLiftId == workoutLiftId }
            ?: return days
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

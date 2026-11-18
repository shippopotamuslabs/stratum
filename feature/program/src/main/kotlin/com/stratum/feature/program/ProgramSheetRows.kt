@file:Suppress("MatchingDeclarationName")

package com.stratum.feature.program

/**
 * Flat row model for the whole-program sheet. No Compose: the list is day order,
 * and scroll math stays a pure function of that list.
 */
internal sealed interface ProgramSheetRow {
    val dayId: String

    data class WorkoutHeader(override val dayId: String) : ProgramSheetRow

    data class ColumnHeader(override val dayId: String) : ProgramSheetRow

    data class Lift(override val dayId: String, val workoutLiftId: String) : ProgramSheetRow

    data class Rest(override val dayId: String) : ProgramSheetRow
}

/** One expanded lift in the sheet. Remembered by the composable, not by the draft. */
internal data class ProgramSheetLiftTarget(val dayId: String, val workoutLiftId: String)

internal enum class ProgramSheetLiftStep { Previous, Next }

internal fun programSheetRows(days: List<CreateProgramDayUi>): List<ProgramSheetRow> =
    buildList {
        for (day in days) {
            val edit = day.editState
            if (edit == null || day.isRest) {
                add(ProgramSheetRow.Rest(day.id))
            } else {
                add(ProgramSheetRow.WorkoutHeader(day.id))
                add(ProgramSheetRow.ColumnHeader(day.id))
                for (lift in edit.lifts) {
                    add(ProgramSheetRow.Lift(dayId = day.id, workoutLiftId = lift.workoutLiftId))
                }
            }
        }
    }

/** Index of that day's [ProgramSheetRow.WorkoutHeader] or [ProgramSheetRow.Rest]. */
internal fun programSheetAnchorIndex(rows: List<ProgramSheetRow>, dayId: String): Int? {
    val index =
        rows.indexOfFirst { row ->
            row.dayId == dayId &&
                (row is ProgramSheetRow.WorkoutHeader || row is ProgramSheetRow.Rest)
        }
    return index.takeIf { it >= 0 }
}

/** Day of the row at [firstVisibleIndex], or null when the index is out of range. */
internal fun programSheetActiveDayId(rows: List<ProgramSheetRow>, firstVisibleIndex: Int): String? =
    rows.getOrNull(firstVisibleIndex)?.dayId

/**
 * Day to select for [visibleIndices].
 * Keeps [latchedDayId] while any of its rows are on screen, so a short sheet
 * or an in-workout scroll does not fall through to the first row. Otherwise
 * the day at the first visible index, or null when nothing is visible.
 */
internal fun programSheetFollowDayId(
    rows: List<ProgramSheetRow>,
    visibleIndices: List<Int>,
    latchedDayId: String?,
): String? {
    val latchVisible =
        latchedDayId != null &&
            visibleIndices.any { index -> rows.getOrNull(index)?.dayId == latchedDayId }
    if (latchVisible) return latchedDayId
    val firstVisibleIndex = visibleIndices.firstOrNull() ?: return null
    return programSheetActiveDayId(rows, firstVisibleIndex)
}

/** Index of that lift row, or null when the lift is not in the sheet. */
internal fun programSheetLiftIndex(
    rows: List<ProgramSheetRow>,
    dayId: String,
    workoutLiftId: String,
): Int? {
    val index =
        rows.indexOfFirst { row ->
            row is ProgramSheetRow.Lift &&
                row.dayId == dayId &&
                row.workoutLiftId == workoutLiftId
        }
    return index.takeIf { it >= 0 }
}

/**
 * Previous or next [ProgramSheetRow.Lift] in sheet order.
 * Headers, column headers, and rest rows are not stops, so a rest day between
 * workouts is skipped. Ends return null.
 */
internal fun programSheetAdjacentLift(
    rows: List<ProgramSheetRow>,
    dayId: String,
    workoutLiftId: String,
    step: ProgramSheetLiftStep,
): ProgramSheetLiftTarget? {
    val lifts = rows.filterIsInstance<ProgramSheetRow.Lift>()
    val index = lifts.indexOfFirst { lift -> lift.dayId == dayId && lift.workoutLiftId == workoutLiftId }
    if (index < 0) return null
    val adjacentIndex =
        when (step) {
            ProgramSheetLiftStep.Previous -> index - 1
            ProgramSheetLiftStep.Next -> index + 1
        }
    val adjacent = lifts.getOrNull(adjacentIndex) ?: return null
    return ProgramSheetLiftTarget(dayId = adjacent.dayId, workoutLiftId = adjacent.workoutLiftId)
}

/**
 * One laid-out sheet row. [stickyHeader] is a workout header pinned over the list;
 * scroll math stays free of Compose types.
 */
internal data class SheetPlacedItem(
    val index: Int,
    val offsetPx: Int,
    val sizePx: Int,
    val stickyHeader: Boolean,
)

/**
 * Pixels of [targetIndex] hidden under a sticky header.
 * A header counts only when it is flagged and its index is before the target.
 * Overlap is the header's end minus the target offset; several headers use the largest.
 * Zero when the target is absent or already clear of every header.
 */
internal fun sheetStickyHeaderOverlapPx(items: List<SheetPlacedItem>, targetIndex: Int): Int {
    val target = items.firstOrNull { item -> item.index == targetIndex } ?: return 0
    val overlap =
        items
            .filter { header -> header.stickyHeader && header.index < targetIndex }
            .maxOfOrNull { header -> header.offsetPx + header.sizePx - target.offsetPx }
            ?: return 0
    return overlap.coerceAtLeast(0)
}

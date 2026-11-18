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

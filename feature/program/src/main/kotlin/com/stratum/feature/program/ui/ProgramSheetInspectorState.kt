package com.stratum.feature.program.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.stratum.feature.program.CreateProgramDayUi
import com.stratum.feature.program.ProgramSheetLiftTarget

/**
 * Which lift the sheet screen has open. The same value is the in-place card
 * on a phone and the inspector selection on a foldable. Posture changes do
 * not replace this object.
 */
internal class ProgramSheetInspectorState(
    initial: ProgramSheetLiftTarget?,
) {
    var selected by mutableStateOf(initial)

    /** Installed by the sheet while it is composed. Commits the dock, then clears [selected]. */
    var onReturnToDashboard: (() -> Unit)? = null

    fun returnToDashboard() {
        onReturnToDashboard?.invoke()
    }
}

/**
 * Revalidates [selected] against the current [days] after a state change that
 * can move or remove lifts without touching the selection (undo is the
 * reported case: it restores the days ViewModel-side and leaves the selection
 * pointing at the pre-undo day). A selection whose lift still sits in its day
 * is returned unchanged; a lift found in another day retargets the selection
 * to that day; a lift found nowhere clears the selection.
 */
internal fun reconcileSheetSelection(
    selected: ProgramSheetLiftTarget?,
    days: List<CreateProgramDayUi>,
): ProgramSheetLiftTarget? {
    if (selected == null) return null
    fun dayContains(day: CreateProgramDayUi): Boolean =
        day.editState?.lifts?.any { lift -> lift.workoutLiftId == selected.workoutLiftId } == true
    val selectedDay = days.firstOrNull { day -> day.id == selected.dayId }
    if (selectedDay != null && dayContains(selectedDay)) return selected
    val home = days.firstOrNull { day -> dayContains(day) }
    return home?.let { day -> selected.copy(dayId = day.id) }
}

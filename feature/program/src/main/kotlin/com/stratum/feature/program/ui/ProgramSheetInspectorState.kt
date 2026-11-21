package com.stratum.feature.program.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

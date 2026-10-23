package com.stratum

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.StratumConfirmDialog

/**
 * Delete confirmation for one Gym catalog lift (STR-131). One variant for
 * every lift — seed and custom alike, delete means delete (no hide variant).
 * The body carries the DB-computed impact from [PendingLiftDeleteUi]: slots
 * that will be removed, logged sets that stay in history, and the
 * active-session grandfather note when it applies (it never blocks).
 */
@Composable
internal fun LiftDeleteConfirmDialog(
    pending: PendingLiftDeleteUi,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val lines = mutableListOf(stringResource(UiR.string.lift_delete_body, pending.name))
    if (pending.affectedWorkouts.isNotEmpty()) {
        val bulletLines =
            pending.affectedWorkouts.map { workout ->
                val label =
                    if (workout.slotCount > 1) {
                        stringResource(UiR.string.lift_delete_workout_slots, workout.name, workout.slotCount)
                    } else {
                        workout.name
                    }
                stringResource(UiR.string.lift_delete_bullet, label)
            }
        lines += stringResource(UiR.string.lift_delete_slots_intro) + "\n" + bulletLines.joinToString("\n")
    }
    if (pending.loggedSetCount > 0) {
        lines +=
            pluralStringResource(UiR.plurals.lift_delete_sets_body, pending.loggedSetCount, pending.loggedSetCount)
    }
    if (pending.inActiveSession) {
        lines += stringResource(UiR.string.lift_delete_session_note)
    }
    StratumConfirmDialog(
        title = stringResource(UiR.string.lift_delete_title, pending.name),
        body = lines.joinToString("\n\n"),
        confirmLabel = stringResource(UiR.string.lift_delete_confirm),
        dismissLabel = stringResource(UiR.string.equipment_dialog_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructiveConfirm = true,
    )
}

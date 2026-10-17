package com.stratum.liftconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumConfirmDialog
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor

/** One replacement candidate in the delete dialog picker (id + display name). */
data class ReplacementOptionUi(
    val id: String,
    val name: String,
)

/** One affected workout in the delete dialog (display name + slot count). */
data class AffectedWorkoutUi(
    val name: String,
    val slotCount: Int,
)

/**
 * Shared delete confirmation for both equipment delete entry points (Manage
 * Equipment swipe-to-dismiss and the increments-editor red delete button), so
 * title/body/label copy and destructive styling cannot drift between them.
 *
 * STR-105: when the equipment still has live pointers, the dialog lists what
 * is affected and requires one replacement for the whole delete (Delete stays
 * disabled until picked); with no candidate it states the Bodyweight fallback.
 */
@Composable
internal fun EquipmentDeleteConfirmDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    affectedLiftNames: List<String> = emptyList(),
    affectedWorkouts: List<AffectedWorkoutUi> = emptyList(),
    candidates: List<ReplacementOptionUi> = emptyList(),
    selectedReplacementId: String? = null,
    usesBodyweightFallback: Boolean = false,
    canConfirmDelete: Boolean? = null,
    onSelectReplacement: (String) -> Unit = {},
) {
    val workoutLabels = mutableListOf<String>()
    for (workout in affectedWorkouts) {
        workoutLabels +=
            if (workout.slotCount > 1) {
                stringResource(UiR.string.equipment_delete_workout_slots, workout.name, workout.slotCount)
            } else {
                workout.name
            }
    }
    val affectedSummary = (affectedLiftNames + workoutLabels).joinToString(", ")
    val hasAffected = affectedSummary.isNotBlank()
    val body = when {
        usesBodyweightFallback ->
            stringResource(UiR.string.equipment_delete_fallback_body, name, affectedSummary)
        hasAffected ->
            stringResource(UiR.string.equipment_delete_affected_body, name, affectedSummary)
        else -> stringResource(UiR.string.equipment_delete_body, name)
    }
    val confirmEnabled = canConfirmDelete ?: (!hasAffected || usesBodyweightFallback || selectedReplacementId != null)
    StratumConfirmDialog(
        title = stringResource(UiR.string.equipment_delete_title, name),
        body = body,
        confirmLabel = stringResource(UiR.string.equipment_delete_confirm),
        dismissLabel = stringResource(UiR.string.equipment_dialog_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructiveConfirm = true,
        confirmEnabled = confirmEnabled,
        content = if (hasAffected && !usesBodyweightFallback) {
            {
                StratumText(
                    text = stringResource(UiR.string.equipment_delete_replacement_label),
                    style = StratumThemeAccessor.typography.sectionLabel,
                    muted = true,
                )
                // STR-134: the candidate list scrolls inside a weighted slot so
                // a long list can never push the Cancel/Delete row off the
                // dialog; short lists size to their content exactly as before.
                Column(
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    candidates.forEach { candidate ->
                        if (candidate.id == selectedReplacementId) {
                            StratumButton(
                                text = candidate.name,
                                onClick = { onSelectReplacement(candidate.id) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            StratumOutlinedButton(
                                text = candidate.name,
                                onClick = { onSelectReplacement(candidate.id) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        } else {
            null
        },
    )
}

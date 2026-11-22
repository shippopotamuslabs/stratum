package com.stratum.liftconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumConfirmDialog
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextButton
import com.stratum.core.ui.theme.StratumSpacing
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

/** Affected entries shown before the "Show all" toggle takes over (STR-334). */
private const val AFFECTED_PREVIEW_COUNT = 3

/** Height cap for the expanded affected list; it scrolls inside this box (STR-334). */
private val AffectedListMaxHeight = 150.dp

/**
 * Expansion state for the affected list in [EquipmentDeleteConfirmDialog]
 * (STR-334). The dialog creates one internally; screenshot fixtures pass a
 * pre-expanded holder so the expanded rendering is reachable in goldens.
 */
internal class AffectedListState(
    initiallyExpanded: Boolean = false,
) {
    var expanded by mutableStateOf(initiallyExpanded)
        private set

    fun toggle() {
        expanded = !expanded
    }
}

@Composable
internal fun rememberAffectedListState(initiallyExpanded: Boolean = false): AffectedListState =
    remember { AffectedListState(initiallyExpanded) }

/**
 * Shared delete confirmation for both equipment delete entry points (Manage
 * Equipment swipe-to-dismiss and the increments-editor red delete button), so
 * title/body/label copy and destructive styling cannot drift between them.
 *
 * STR-105: when the equipment still has live pointers, the dialog states what
 * is affected and requires one replacement for the whole delete (Delete stays
 * disabled until picked); with no candidate it states the Bodyweight fallback.
 *
 * STR-334: the body carries the affected count only. The affected names render
 * as a short preview list in the content slot, with the full list behind a
 * toggle inside a height-capped scroll box, so a heavily used piece of
 * equipment (a cable station with 60+ lifts) can never push the Cancel/Delete
 * row off the dialog the way the old inline enumeration did.
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
    affectedListState: AffectedListState = rememberAffectedListState(),
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
    val affectedLabels = affectedLiftNames + workoutLabels
    val hasAffected = affectedLabels.isNotEmpty()
    val affectedCountPhrase =
        pluralStringResource(
            UiR.plurals.equipment_delete_affected_count,
            affectedLabels.size,
            affectedLabels.size,
        )
    val body = when {
        usesBodyweightFallback ->
            stringResource(UiR.string.equipment_delete_fallback_body, name, affectedCountPhrase)
        hasAffected ->
            stringResource(UiR.string.equipment_delete_affected_body, name, affectedCountPhrase)
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
        content = if (hasAffected) {
            {
                AffectedLabelsList(
                    labels = affectedLabels,
                    state = affectedListState,
                )
                if (!usesBodyweightFallback) {
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
                        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
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
            }
        } else {
            null
        },
    )
}

/**
 * STR-334: the affected lifts/workouts as a preview of the first few names.
 * Past [AFFECTED_PREVIEW_COUNT] entries a toggle expands the full list inside
 * a height-capped scroll box; collapsing returns to the preview. The box cap
 * keeps the dialog height bounded no matter how many entries expand into it.
 */
@Composable
private fun AffectedLabelsList(
    labels: List<String>,
    state: AffectedListState,
) {
    val showToggle = labels.size > AFFECTED_PREVIEW_COUNT
    val isExpanded = showToggle && state.expanded
    val visibleLabels = if (isExpanded) labels else labels.take(AFFECTED_PREVIEW_COUNT)
    val scrollState = rememberScrollState()
    val listModifier =
        if (isExpanded) {
            Modifier
                .heightIn(max = AffectedListMaxHeight)
                .verticalScroll(scrollState)
        } else {
            Modifier
        }
    Column(
        modifier = listModifier,
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
    ) {
        visibleLabels.forEach { label ->
            StratumText(
                text = label,
                style = StratumThemeAccessor.typography.bodyMedium,
                muted = true,
            )
        }
    }
    if (showToggle) {
        StratumTextButton(
            text =
                if (state.expanded) {
                    stringResource(UiR.string.equipment_delete_show_less)
                } else {
                    stringResource(UiR.string.equipment_delete_show_all, labels.size)
                },
            onClick = { state.toggle() },
        )
    }
}

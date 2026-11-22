package com.stratum.liftconfig

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumSearchField
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor

/** One replacement candidate in the delete flow picker (id + display name). */
data class ReplacementOptionUi(
    val id: String,
    val name: String,
)

/** One affected workout in the delete flow (display name + slot count). */
data class AffectedWorkoutUi(
    val name: String,
    val slotCount: Int,
)

/**
 * Shared delete flow for both equipment delete entry points (Manage Equipment
 * and the increments editor), so copy and destructive styling cannot drift
 * between them.
 *
 * STR-105: when the equipment still has live pointers, the flow states what is
 * affected and requires one replacement for the whole delete (Delete stays
 * disabled until picked); with no candidate it states the Bodyweight fallback.
 *
 * STR-335: the flow is a tall bottom sheet plus a full-screen replacement
 * picker, replacing the STR-334 confirm dialog. The linked lifts and workouts
 * get their own scroll region inside the sheet, the replacement is picked from
 * the whole catalog on a full screen with search, and Cancel/Delete stay
 * pinned at the bottom of the sheet.
 */
@Composable
internal fun EquipmentDeleteSheet(
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
    var pickerOpen by remember { mutableStateOf(false) }
    StratumBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(UiR.string.equipment_delete_title, name),
    ) {
        EquipmentDeleteSheetContent(
            name = name,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            affectedLiftNames = affectedLiftNames,
            affectedWorkouts = affectedWorkouts,
            candidates = candidates,
            selectedReplacementId = selectedReplacementId,
            usesBodyweightFallback = usesBodyweightFallback,
            canConfirmDelete = canConfirmDelete,
            onOpenPicker = { pickerOpen = true },
        )
    }
    if (pickerOpen) {
        ReplacementCatalogPicker(
            candidates = candidates,
            selectedReplacementId = selectedReplacementId,
            onSelect = { id ->
                onSelectReplacement(id)
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

/** Sheet body without the modal chrome, so previews and tests can render it inline. */
@Composable
internal fun ColumnScope.EquipmentDeleteSheetContent(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    affectedLiftNames: List<String>,
    affectedWorkouts: List<AffectedWorkoutUi>,
    candidates: List<ReplacementOptionUi>,
    selectedReplacementId: String?,
    usesBodyweightFallback: Boolean,
    canConfirmDelete: Boolean?,
    onOpenPicker: () -> Unit,
) {
    val workoutLabels = affectedWorkouts.map { workout ->
        if (workout.slotCount > 1) {
            stringResource(UiR.string.equipment_delete_workout_slots, workout.name, workout.slotCount)
        } else {
            workout.name
        }
    }
    val hasAffected = affectedLiftNames.isNotEmpty() || workoutLabels.isNotEmpty()
    val affectedCount = affectedLiftNames.size + workoutLabels.size
    val affectedCountPhrase =
        pluralStringResource(
            UiR.plurals.equipment_delete_affected_count,
            affectedCount,
            affectedCount,
        )
    val body = when {
        usesBodyweightFallback ->
            stringResource(UiR.string.equipment_delete_fallback_body, name, affectedCountPhrase)
        hasAffected ->
            stringResource(UiR.string.equipment_delete_affected_body, name, affectedCountPhrase)
        else -> stringResource(UiR.string.equipment_delete_body, name)
    }
    val confirmEnabled = canConfirmDelete ?: (!hasAffected || usesBodyweightFallback || selectedReplacementId != null)
    StratumText(
        text = body,
        style = StratumThemeAccessor.typography.bodyLarge,
    )
    if (hasAffected) {
        // Weighted (not greedy): the linked list scrolls inside the sheet's
        // remaining space, so any link count leaves the sections below and the
        // Cancel/Delete row fully on screen.
        Column(
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
        ) {
            if (affectedLiftNames.isNotEmpty()) {
                StratumText(
                    text = stringResource(UiR.string.equipment_delete_lifts_label),
                    style = StratumThemeAccessor.typography.sectionLabel,
                    muted = true,
                )
                affectedLiftNames.forEach { liftName ->
                    StratumText(
                        text = liftName,
                        style = StratumThemeAccessor.typography.bodyLarge,
                    )
                }
            }
            if (workoutLabels.isNotEmpty()) {
                StratumText(
                    text = stringResource(UiR.string.equipment_delete_workouts_label),
                    style = StratumThemeAccessor.typography.sectionLabel,
                    muted = true,
                    modifier = Modifier.padding(top = StratumSpacing.s8),
                )
                workoutLabels.forEach { label ->
                    StratumText(
                        text = label,
                        style = StratumThemeAccessor.typography.bodyLarge,
                    )
                }
            }
        }
        if (!usesBodyweightFallback) {
            StratumText(
                text = stringResource(UiR.string.equipment_delete_replacement_label),
                style = StratumThemeAccessor.typography.sectionLabel,
                muted = true,
            )
            ReplacementPickerRow(
                selectedName = candidates.firstOrNull { it.id == selectedReplacementId }?.name,
                onClick = onOpenPicker,
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
    ) {
        StratumOutlinedButton(
            text = stringResource(UiR.string.equipment_dialog_cancel),
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
        )
        StratumButton(
            text = stringResource(UiR.string.equipment_delete_confirm),
            onClick = onConfirm,
            enabled = confirmEnabled,
            destructive = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ReplacementPickerRow(
    selectedName: String?,
    onClick: () -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(width = shapes.borderWidth, color = colors.borderSubtle, shape = shapes.panel)
                .clickable(onClick = onClick)
                .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s12),
        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StratumText(
            text = selectedName ?: stringResource(UiR.string.equipment_delete_choose_replacement),
            style = StratumThemeAccessor.typography.bodyLarge,
            muted = selectedName == null,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(UiR.drawable.ic_chevron_right),
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * STR-335: the replacement catalog as a full screen. Candidates are the whole
 * live catalog apart from the equipment being deleted, so the list gets the
 * full display with search. Tapping a candidate picks it and returns to the
 * sheet; backing out keeps the previous pick.
 */
@Composable
internal fun ReplacementCatalogPicker(
    candidates: List<ReplacementOptionUi>,
    selectedReplacementId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ReplacementCatalogPickerContent(
            candidates = candidates,
            selectedReplacementId = selectedReplacementId,
            onSelect = onSelect,
            onBack = onDismiss,
        )
    }
}

/** Picker body without the dialog chrome, so previews and tests can render it inline. */
@Composable
internal fun ReplacementCatalogPickerContent(
    candidates: List<ReplacementOptionUi>,
    selectedReplacementId: String?,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    var query by remember { mutableStateOf("") }
    val matches =
        if (query.isBlank()) {
            candidates
        } else {
            candidates.filter { it.name.contains(query.trim(), ignoreCase = true) }
        }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.surfaceBase)
                .padding(horizontal = StratumSpacing.s16)
                .padding(top = StratumSpacing.s12, bottom = StratumSpacing.s32),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StratumIconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_chevron_left),
                    contentDescription = stringResource(UiR.string.equipment_delete_picker_back),
                    tint = colors.textPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
            StratumText(
                text = stringResource(UiR.string.equipment_delete_choose_replacement),
                style = StratumThemeAccessor.typography.titleLarge,
            )
        }
        StratumSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(UiR.string.equipment_swap_search_placeholder),
        )
        if (matches.isEmpty()) {
            StratumText(
                text = stringResource(UiR.string.equipment_swap_no_match, query.trim()),
                style = StratumThemeAccessor.typography.bodyLarge,
                muted = true,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
            ) {
                items(matches, key = { it.id }) { candidate ->
                    if (candidate.id == selectedReplacementId) {
                        StratumButton(
                            text = candidate.name,
                            onClick = { onSelect(candidate.id) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        StratumOutlinedButton(
                            text = candidate.name,
                            onClick = { onSelect(candidate.id) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

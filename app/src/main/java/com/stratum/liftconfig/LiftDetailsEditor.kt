package com.stratum.liftconfig

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.MuscleFilterBottomSheet
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumDurationStepper
import com.stratum.core.ui.component.StratumFilterChip
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumMuscleFilterCard
import com.stratum.core.ui.component.StratumMuscleFilterCardState
import com.stratum.core.ui.component.StratumPillToggle
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextField
import com.stratum.core.ui.component.defaultMuscleFilterTiers
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.SetLiftDefaultRestUseCase
import com.stratum.domain.onboarding.MuscleGroup

enum class MuscleRoleFilter {
    Primary,
    Secondary,
}

/**
 * Which Details-tab field sheet is open. Catalog metadata editors require a custom lift;
 * [DefaultRest] is allowed for every lift (personal programming preference).
 */
enum class LiftDetailEditField {
    LengthBias,
    MovementPatterns,
    Fatigue,
    LoadType,
    Equipment,
    DefaultRest,
}

data class LiftDetailOptionUi(
    val key: String,
    val label: String,
    val selected: Boolean,
    /** Optional supporting line under [label] (e.g. fatigue tier meaning). */
    val description: String? = null,
)

/** Presentation model for the shared Details editor (Information + Create Lift). */
data class LiftDetailsEditorState(
    val canEdit: Boolean = false,
    val muscleRole: MuscleRoleFilter = MuscleRoleFilter.Primary,
    val primaryMuscles: List<StratumMuscleFilterCardState> = emptyList(),
    val secondaryMuscles: List<StratumMuscleFilterCardState> = emptyList(),
    val lengthBiasLabel: String = "",
    val movementPatternLabels: List<String> = emptyList(),
    val fatigueLabel: String = "",
    val loadTypeLabel: String = "",
    val equipment: String? = null,
    /**
     * Derived Gym / swap category label. Non-null shows the row (Lift Info);
     * null omits it (Create — not actionable there).
     */
    val categoryLabel: String? = null,
    val defaultRestLabel: String = "",
    val showAddMuscleSheet: Boolean = false,
    val editingField: LiftDetailEditField? = null,
    val lengthBiasOptions: List<LiftDetailOptionUi> = emptyList(),
    val fatigueOptions: List<LiftDetailOptionUi> = emptyList(),
    val loadTypeOptions: List<LiftDetailOptionUi> = emptyList(),
    val movementPatternOptions: List<LiftDetailOptionUi> = emptyList(),
    val restDraftSeconds: Int = 120,
)

/** Callbacks for Details muscle edits and field editors. */
data class LiftDetailsEditorActions(
    val onMuscleRoleSelected: (MuscleRoleFilter) -> Unit,
    val onShowAddMuscleSheet: (Boolean) -> Unit,
    val onAddMuscle: (MuscleGroup) -> Unit,
    val onRemoveMuscle: (MuscleGroup) -> Unit,
    val onOpenDetailEditor: (LiftDetailEditField) -> Unit,
    val onDismissDetailEditor: () -> Unit,
    val onSelectLengthBias: (String) -> Unit,
    val onSelectFatigue: (String) -> Unit,
    val onSelectLoadType: (String) -> Unit,
    val onToggleDraftMovementPattern: (String) -> Unit,
    val onConfirmMovementPatterns: () -> Unit,
    /** Assign bodyweight-only equipment (no external load implement). Does not change load type. */
    val onAssignBodyweightEquipment: () -> Unit,
    /** Navigate to full Choose Equipment screen. */
    val onChooseEquipment: () -> Unit,
    val onRestDraftChange: (Int) -> Unit,
    val onSaveDefaultRest: () -> Unit,
)

/**
 * Shared Details body + editor sheets used by Lift Information and Create Lift.
 * Domain models are never passed in — [state] is presentation-only.
 */
@Composable
fun LiftDetailsEditor(
    state: LiftDetailsEditorState,
    actions: LiftDetailsEditorActions,
    modifier: Modifier = Modifier,
) {
    LiftDetailsEditorContent(
        state = state,
        onMuscleRoleSelected = actions.onMuscleRoleSelected,
        onShowAddMuscleSheet = actions.onShowAddMuscleSheet,
        onRemoveMuscle = actions.onRemoveMuscle,
        onOpenDetailEditor = actions.onOpenDetailEditor,
        modifier = modifier,
    )

    if (state.showAddMuscleSheet && state.canEdit) {
        val selected =
            when (state.muscleRole) {
                MuscleRoleFilter.Primary -> state.primaryMuscles.map { it.muscle }.toSet()
                MuscleRoleFilter.Secondary -> state.secondaryMuscles.map { it.muscle }.toSet()
            }
        MuscleFilterBottomSheet(
            onDismissRequest = { actions.onShowAddMuscleSheet(false) },
            selectedMuscles = selected,
            muscleGroups = defaultMuscleFilterTiers(),
            // Multi-select: keep the sheet open until Done; toggle adds or removes.
            onMuscleToggled = { muscle ->
                if (muscle in selected) {
                    actions.onRemoveMuscle(muscle)
                } else {
                    actions.onAddMuscle(muscle)
                }
            },
            onClearAll = {
                selected.forEach { muscle -> actions.onRemoveMuscle(muscle) }
            },
        )
    }

    when (state.editingField) {
        LiftDetailEditField.LengthBias ->
            SingleSelectDetailSheet(
                title = stringResource(UiR.string.lift_info_length_bias),
                options = state.lengthBiasOptions,
                onDismiss = actions.onDismissDetailEditor,
                onSelect = actions.onSelectLengthBias,
            )
        LiftDetailEditField.Fatigue ->
            SingleSelectDetailSheet(
                title = stringResource(UiR.string.lift_info_fatigue_profile),
                options =
                    state.fatigueOptions.map { option ->
                        option.copy(description = fatigueTierDescription(option.key))
                    },
                onDismiss = actions.onDismissDetailEditor,
                onSelect = actions.onSelectFatigue,
            )
        LiftDetailEditField.LoadType ->
            SingleSelectDetailSheet(
                title = stringResource(UiR.string.lift_info_load_type),
                options = state.loadTypeOptions,
                onDismiss = actions.onDismissDetailEditor,
                onSelect = actions.onSelectLoadType,
            )
        LiftDetailEditField.MovementPatterns ->
            MultiSelectDetailSheet(
                title = stringResource(UiR.string.lift_info_movement_patterns),
                options = state.movementPatternOptions,
                confirmLabel = stringResource(UiR.string.lift_info_save_patterns),
                onDismiss = actions.onDismissDetailEditor,
                onToggle = actions.onToggleDraftMovementPattern,
                onConfirm = actions.onConfirmMovementPatterns,
            )
        LiftDetailEditField.Equipment ->
            EquipmentActionSheet(
                onDismiss = actions.onDismissDetailEditor,
                onAssignBodyweight = actions.onAssignBodyweightEquipment,
                onChooseEquipment = actions.onChooseEquipment,
            )
        LiftDetailEditField.DefaultRest ->
            DefaultRestBottomSheet(
                totalSeconds = state.restDraftSeconds,
                onTotalSecondsChange = actions.onRestDraftChange,
                onSave = actions.onSaveDefaultRest,
                onDismiss = actions.onDismissDetailEditor,
            )
        null -> Unit
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiftDetailsEditorContent(
    state: LiftDetailsEditorState,
    onMuscleRoleSelected: (MuscleRoleFilter) -> Unit,
    onShowAddMuscleSheet: (Boolean) -> Unit,
    onRemoveMuscle: (MuscleGroup) -> Unit,
    onOpenDetailEditor: (LiftDetailEditField) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val canEdit = state.canEdit
    val cards =
        when (state.muscleRole) {
            MuscleRoleFilter.Primary -> state.primaryMuscles
            MuscleRoleFilter.Secondary -> state.secondaryMuscles
        }
    val specRows =
        buildList {
            add(
                DetailSpec(
                    label = stringResource(UiR.string.lift_info_length_bias),
                    value = state.lengthBiasLabel.ifBlank { "—" },
                    editField = LiftDetailEditField.LengthBias,
                ),
            )
            add(
                DetailSpec(
                    label = stringResource(UiR.string.lift_info_movement_patterns),
                    value = state.movementPatternLabels.joinToString(", ").ifBlank { "—" },
                    editField = LiftDetailEditField.MovementPatterns,
                ),
            )
            add(
                DetailSpec(
                    label = stringResource(UiR.string.lift_info_fatigue_profile),
                    value = state.fatigueLabel.ifBlank { "—" },
                    editField = LiftDetailEditField.Fatigue,
                ),
            )
            add(
                DetailSpec(
                    label = stringResource(UiR.string.lift_info_load_type),
                    value = state.loadTypeLabel.ifBlank { "—" },
                    editField = LiftDetailEditField.LoadType,
                ),
            )
            state.categoryLabel?.let { label ->
                add(
                    DetailSpec(
                        label = stringResource(UiR.string.lift_info_category),
                        value = label.ifBlank { "—" },
                        // Derived from patterns + primary muscles — never edited directly.
                        editField = null,
                    ),
                )
            }
            add(
                DetailSpec(
                    label = stringResource(UiR.string.lift_info_equipment),
                    value = state.equipment?.ifBlank { null } ?: "—",
                    editField = LiftDetailEditField.Equipment,
                    // Catalog and custom lifts may reassign equipment for shared increments.
                    alwaysEditable = true,
                ),
            )
            add(
                DetailSpec(
                    label = stringResource(UiR.string.lift_info_default_rest),
                    value = state.defaultRestLabel.ifBlank { "—" },
                    editField = LiftDetailEditField.DefaultRest,
                    // Personal programming preference — editable for catalog and custom lifts.
                    alwaysEditable = true,
                ),
            )
        }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StratumText(text = stringResource(UiR.string.lift_info_target_muscles))
                        if (canEdit) {
                            StratumIconButton(
                                onClick = { onShowAddMuscleSheet(true) },
                                containerColor = colors.surfaceRaised,
                                contentColor = colors.textPrimary,
                                modifier = Modifier.size(DetailsAddButtonSize),
                            ) {
                                Icon(
                                    painter = painterResource(UiR.drawable.ic_add),
                                    contentDescription =
                                        stringResource(UiR.string.lift_info_add_muscle),
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(DetailsAddIconSize),
                                )
                            }
                        }
                    }
                    StratumPillToggle(
                        segments =
                            listOf(
                                stringResource(UiR.string.lift_info_primary),
                                stringResource(UiR.string.lift_info_secondary),
                            ),
                        selectedIndex =
                            if (state.muscleRole == MuscleRoleFilter.Primary) 0 else 1,
                        onSelectedIndexChange = { index ->
                            onMuscleRoleSelected(
                                if (index == 0) {
                                    MuscleRoleFilter.Primary
                                } else {
                                    MuscleRoleFilter.Secondary
                                },
                            )
                        },
                    )
                }
                if (cards.isEmpty()) {
                    StratumText(
                        text = stringResource(UiR.string.lift_info_no_muscles),
                        muted = true,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        cards.forEach { card ->
                            StratumMuscleFilterCard(
                                state = card,
                                selected = true,
                                onClick =
                                    if (canEdit) {
                                        { onRemoveMuscle(card.muscle) }
                                    } else {
                                        {}
                                    },
                                // Width only — height wraps anatomy + label so cards are not tall.
                                modifier = Modifier.width(DetailsMuscleCardWidth),
                            )
                        }
                    }
                }
            }
        }
        item {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(color = colors.surfaceRaised, shape = shapes.panel)
                        .border(
                            width = shapes.borderWidth,
                            color = colors.borderSubtle,
                            shape = shapes.panel,
                        )
                        .padding(horizontal = 14.dp, vertical = 4.dp),
            ) {
                specRows.forEachIndexed { index, row ->
                    val editable =
                        row.editField != null && (canEdit || row.alwaysEditable)
                    DetailSpecRow(
                        label = row.label,
                        value = row.value,
                        editable = editable,
                        onClick =
                            row.editField
                                ?.takeIf { editable }
                                ?.let { field -> { onOpenDetailEditor(field) } },
                    )
                    if (index != specRows.lastIndex) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(colors.borderSubtle),
                        )
                    }
                }
            }
        }
    }
}

private data class DetailSpec(
    val label: String,
    val value: String,
    val editField: LiftDetailEditField?,
    val alwaysEditable: Boolean = false,
)

@Composable
private fun DetailSpecRow(
    label: String,
    value: String,
    editable: Boolean,
    onClick: (() -> Unit)?,
) {
    val colors = StratumThemeAccessor.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StratumText(
            text = label,
            muted = true,
            modifier = Modifier.weight(1f),
        )
        StratumText(
            text = value,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
        if (editable) {
            Icon(
                painter = painterResource(UiR.drawable.ic_chevron_right),
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SingleSelectDetailSheet(
    title: String,
    options: List<LiftDetailOptionUi>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    StratumBottomSheet(onDismissRequest = onDismiss, title = title) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                DetailOptionRow(
                    label = option.label,
                    description = option.description,
                    selected = option.selected,
                    onClick = { onSelect(option.key) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiSelectDetailSheet(
    title: String,
    options: List<LiftDetailOptionUi>,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    StratumBottomSheet(onDismissRequest = onDismiss, title = title) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                StratumFilterChip(
                    label = option.label,
                    selected = option.selected,
                    onSelectedChange = { onToggle(option.key) },
                )
            }
        }
        StratumButton(
            text = confirmLabel,
            modifier = Modifier.fillMaxWidth(),
            enabled = options.any { it.selected },
            onClick = onConfirm,
        )
    }
}

/**
 * Two-option sheet: assign bodyweight-only equipment, or open Manage Equipment (selection mode).
 */
@Composable
private fun EquipmentActionSheet(
    onDismiss: () -> Unit,
    onAssignBodyweight: () -> Unit,
    onChooseEquipment: () -> Unit,
) {
    StratumBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(UiR.string.lift_info_equipment),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EquipmentActionRow(
                label = stringResource(UiR.string.lift_info_equipment_bodyweight),
                showChevron = false,
                onClick = onAssignBodyweight,
            )
            EquipmentActionRow(
                label = stringResource(UiR.string.lift_info_equipment_choose),
                showChevron = true,
                onClick = onChooseEquipment,
            )
        }
    }
}

@Composable
private fun EquipmentActionRow(
    label: String,
    showChevron: Boolean,
    onClick: () -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StratumText(
            text = label,
            style = StratumThemeAccessor.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (showChevron) {
            Icon(
                painter = painterResource(UiR.drawable.ic_chevron_right),
                contentDescription = null,
                tint = colors.textMuted,
            )
        }
    }
}

/**
 * Same duration stepper as Edit Workout rest sheet; single Save applies the lift-level default
 * (not per-set apply actions).
 */
@Composable
private fun DefaultRestBottomSheet(
    totalSeconds: Int,
    onTotalSecondsChange: (Int) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    StratumBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(UiR.string.lift_info_rest_sheet_title),
    ) {
        StratumDurationStepper(
            totalMinutes = totalSeconds,
            onTotalMinutesChange = onTotalSecondsChange,
            minMinutes = SetLiftDefaultRestUseCase.MIN_REST_SECONDS,
            maxMinutes = SetLiftDefaultRestUseCase.MAX_REST_SECONDS,
            minutesStep = REST_SECONDS_STEP,
            hoursLabel = stringResource(UiR.string.lift_info_rest_minutes_label),
            minutesLabel = stringResource(UiR.string.lift_info_rest_seconds_label),
            incrementContentDescription = stringResource(UiR.string.lift_info_rest_increment),
            decrementContentDescription = stringResource(UiR.string.lift_info_rest_decrement),
            modifier = Modifier.fillMaxWidth(),
        )
        StratumButton(
            text = stringResource(UiR.string.lift_info_rest_save),
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val REST_SECONDS_STEP = 30

@Composable
private fun DetailOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    description: String? = null,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color = if (selected) colors.surfaceSunken else colors.surfaceBase,
                    shape = shapes.panel,
                )
                .border(
                    width = shapes.borderWidth,
                    color = if (selected) colors.accent else colors.borderSubtle,
                    shape = shapes.panel,
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            StratumText(text = label)
            if (!description.isNullOrBlank()) {
                StratumText(
                    text = description,
                    style = StratumThemeAccessor.typography.sectionLabel,
                    muted = true,
                )
            }
        }
        if (selected) {
            StratumText(
                text = stringResource(UiR.string.lift_info_selected_marker),
                muted = true,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/** Supporting copy for fatigue bottom-sheet options (keyed by [FatigueProfile] name). */
@Composable
private fun fatigueTierDescription(key: String): String =
    when (key) {
        FatigueProfile.High.name -> stringResource(UiR.string.lift_info_fatigue_desc_high)
        FatigueProfile.Medium.name -> stringResource(UiR.string.lift_info_fatigue_desc_medium)
        FatigueProfile.Low.name -> stringResource(UiR.string.lift_info_fatigue_desc_low)
        else -> ""
    }

private val DetailsMuscleCardWidth = 76.dp
/** Matches UI_DESIGN.md §5 minimum touch target (48dp). */
private val DetailsAddButtonSize = 48.dp
private val DetailsAddIconSize = 24.dp

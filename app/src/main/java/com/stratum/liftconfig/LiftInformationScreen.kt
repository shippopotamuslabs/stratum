package com.stratum.liftconfig

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.LiftSwapMuscleTiers
import com.stratum.core.ui.component.MuscleFilterBottomSheet
import com.stratum.core.ui.component.RirTargetBadge
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumDurationStepper
import com.stratum.core.ui.component.StratumFilterChip
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumLiftImage
import com.stratum.core.ui.component.StratumMuscleFilterCard
import com.stratum.core.ui.component.StratumMuscleFilterCardState
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumPillToggle
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumSegmentedControl
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextField
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.component.toVolumeDisplayName
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.domain.lift.SetLiftDefaultRestUseCase
import com.stratum.domain.onboarding.MuscleGroup

@Composable
fun LiftInformationRoute(
    liftId: String,
    onBackClick: () -> Unit,
    viewModel: LiftInformationViewModel = hiltViewModel(),
) {
    LaunchedEffect(liftId) {
        viewModel.initialize(liftId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LiftInformationScreen(
        state = state,
        onBackClick = onBackClick,
        actions =
            LiftInformationActions(
                onTabSelected = viewModel::onTabSelected,
                onMuscleRoleSelected = viewModel::onMuscleRoleSelected,
                onSessionExpandedToggled = viewModel::onSessionExpandedToggled,
                onShowAddMuscleSheet = viewModel::onShowAddMuscleSheet,
                onAddMuscle = viewModel::onAddMuscle,
                onRemoveMuscle = viewModel::onRemoveMuscle,
                onOpenDetailEditor = viewModel::onOpenDetailEditor,
                onDismissDetailEditor = viewModel::onDismissDetailEditor,
                onSelectLengthBias = viewModel::onSelectLengthBias,
                onSelectKinematic = viewModel::onSelectKinematic,
                onSelectLoadType = viewModel::onSelectLoadType,
                onToggleDraftMovementPattern = viewModel::onToggleDraftMovementPattern,
                onConfirmMovementPatterns = viewModel::onConfirmMovementPatterns,
                onEquipmentDraftChange = viewModel::onEquipmentDraftChange,
                onSaveEquipment = viewModel::onSaveEquipment,
                onRestDraftChange = viewModel::onRestDraftChange,
                onSaveDefaultRest = viewModel::onSaveDefaultRest,
            ),
    )
}

@Composable
fun LiftInformationScreen(
    state: LiftInformationUiState,
    onBackClick: () -> Unit,
    actions: LiftInformationActions,
    modifier: Modifier = Modifier,
) {
    val tabs =
        listOf(
            LiftInfoTab.Instructions to stringResource(UiR.string.lift_info_tab_instructions),
            LiftInfoTab.Details to stringResource(UiR.string.lift_info_tab_details),
            LiftInfoTab.History to stringResource(UiR.string.lift_info_tab_history),
        )
    StratumScaffold(
        modifier = modifier,
        topBar = {
            StratumTopBar(
                title = state.liftName.ifBlank { stringResource(UiR.string.lift_info_title) },
                navigationIcon = {
                    StratumTopBarBackButton(
                        onClick = onBackClick,
                        contentDescription = stringResource(UiR.string.lift_config_navigate_back),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StratumSegmentedControl(
                modifier = Modifier.fillMaxWidth(),
                segments = tabs.map { it.second },
                selectedIndex = tabs.indexOfFirst { it.first == state.selectedTab }.coerceAtLeast(0),
                onSelectedIndexChange = { index -> actions.onTabSelected(tabs[index].first) },
            )
            when (state.selectedTab) {
                LiftInfoTab.Instructions -> InstructionsTab(state)
                LiftInfoTab.Details ->
                    DetailsTab(
                        state = state,
                        onMuscleRoleSelected = actions.onMuscleRoleSelected,
                        onShowAddMuscleSheet = actions.onShowAddMuscleSheet,
                        onRemoveMuscle = actions.onRemoveMuscle,
                        onOpenDetailEditor = actions.onOpenDetailEditor,
                    )
                LiftInfoTab.History ->
                    HistoryTab(
                        state = state,
                        onSessionExpandedToggled = actions.onSessionExpandedToggled,
                    )
            }
        }
    }

    if (state.showAddMuscleSheet && state.isCustom) {
        val selected =
            when (state.muscleRole) {
                MuscleRoleFilter.Primary -> state.primaryMuscles.map { it.muscle }.toSet()
                MuscleRoleFilter.Secondary -> state.secondaryMuscles.map { it.muscle }.toSet()
            }
        MuscleFilterBottomSheet(
            onDismissRequest = { actions.onShowAddMuscleSheet(false) },
            selectedMuscles = selected,
            muscleGroups = allMuscleTiers(),
            onMuscleToggled = { muscle ->
                if (muscle !in selected) actions.onAddMuscle(muscle)
            },
            onClearAll = { actions.onShowAddMuscleSheet(false) },
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
        LiftDetailEditField.Kinematic ->
            SingleSelectDetailSheet(
                title = stringResource(UiR.string.lift_info_kinematic),
                options = state.kinematicOptions,
                onDismiss = actions.onDismissDetailEditor,
                onSelect = actions.onSelectKinematic,
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
            EquipmentDetailSheet(
                title = stringResource(UiR.string.lift_info_equipment),
                value = state.equipmentDraft,
                onValueChange = actions.onEquipmentDraftChange,
                onDismiss = actions.onDismissDetailEditor,
                onSave = actions.onSaveEquipment,
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

@Composable
private fun InstructionsTab(state: LiftInformationUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (state.imageUrls.isEmpty()) {
            item {
                StratumLiftImage(name = state.liftName, imageUrl = null)
            }
        } else {
            items(state.imageUrls, key = { it }) { url ->
                StratumLiftImage(name = state.liftName, imageUrl = url)
            }
        }
        item {
            val description = state.description
            if (description.isNullOrBlank()) {
                StratumText(
                    text = stringResource(UiR.string.lift_info_no_description),
                    muted = true,
                )
            } else {
                FormattedDescription(description)
            }
        }
    }
}

@Composable
private fun FormattedDescription(text: String) {
    val annotated =
        buildAnnotatedString {
            val parts = text.split(Regex("(?=Tip:)"))
            parts.forEachIndexed { index, part ->
                if (part.startsWith("Tip:")) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("Tip:")
                    }
                    append(part.removePrefix("Tip:"))
                } else {
                    append(part)
                }
                if (index != parts.lastIndex) append(" ")
            }
        }
    StratumText(text = annotated.text)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsTab(
    state: LiftInformationUiState,
    onMuscleRoleSelected: (MuscleRoleFilter) -> Unit,
    onShowAddMuscleSheet: (Boolean) -> Unit,
    onRemoveMuscle: (MuscleGroup) -> Unit,
    onOpenDetailEditor: (LiftDetailEditField) -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val canEdit = state.isCustom
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
                    label = stringResource(UiR.string.lift_info_kinematic),
                    value = state.kinematicLabel.ifBlank { "—" },
                    editField = LiftDetailEditField.Kinematic,
                ),
            )
            add(
                DetailSpec(
                    label = stringResource(UiR.string.lift_info_load_type),
                    value = state.loadTypeLabel.ifBlank { "—" },
                    editField = LiftDetailEditField.LoadType,
                ),
            )
            add(
                DetailSpec(
                    label = stringResource(UiR.string.lift_info_category),
                    value = state.categoryLabel.ifBlank { "—" },
                    // Derived from patterns + primary muscles — never edited directly.
                    editField = null,
                ),
            )
            if (canEdit || !state.equipment.isNullOrBlank()) {
                add(
                    DetailSpec(
                        label = stringResource(UiR.string.lift_info_equipment),
                        value = state.equipment?.ifBlank { null } ?: "—",
                        editField = LiftDetailEditField.Equipment,
                    ),
                )
            }
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
        modifier = Modifier.fillMaxSize(),
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

@Composable
private fun EquipmentDetailSheet(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    StratumBottomSheet(onDismissRequest = onDismiss, title = title) {
        StratumTextField(
            value = value,
            onValueChange = onValueChange,
            label = title,
            placeholder = stringResource(UiR.string.lift_info_equipment_placeholder),
        )
        StratumButton(
            text = stringResource(UiR.string.lift_info_save_equipment),
            modifier = Modifier.fillMaxWidth(),
            onClick = onSave,
        )
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
        StratumText(text = label)
        if (selected) {
            StratumText(
                text = stringResource(UiR.string.lift_info_selected_marker),
                muted = true,
            )
        }
    }
}

private val DetailsMuscleCardWidth = 76.dp
private val DetailsAddButtonSize = 32.dp
private val DetailsAddIconSize = 18.dp

@Composable
private fun HistoryTab(
    state: LiftInformationUiState,
    onSessionExpandedToggled: (String) -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            HistoryPersonalRecordCard(personalRecord = state.personalRecord)
        }
        if (state.historySessions.isEmpty()) {
            item {
                StratumText(
                    text = stringResource(UiR.string.lift_info_no_history),
                    muted = true,
                )
            }
        } else {
            items(state.historySessions, key = { it.sessionId }) { session ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    HistorySessionRow(
                        session = session,
                        onToggle = { onSessionExpandedToggled(session.sessionId) },
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .height(shapes.borderWidth)
                                .background(colors.borderSubtle),
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryPersonalRecordCard(
    personalRecord: LiftPrUi?,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
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
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StratumText(
                text = stringResource(UiR.string.lift_info_personal_record),
                style = StratumThemeAccessor.typography.sectionLabel,
                muted = true,
            )
            Icon(
                painter = painterResource(UiR.drawable.ic_personal_record),
                contentDescription = stringResource(UiR.string.lift_info_personal_record),
                tint = colors.prHighlight,
                modifier = Modifier.size(20.dp),
            )
        }
        if (personalRecord == null) {
            StratumText(
                text = stringResource(UiR.string.lift_info_no_pr),
                muted = true,
            )
        } else {
            StratumText(text = personalRecord.dateText, muted = true)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StratumText(text = personalRecord.weightRepsText)
                if (personalRecord.rir != null) {
                    RirTargetBadge(
                        rirGoal = personalRecord.rir,
                        onClick = {},
                        enabled = false,
                        size = HistoryPrRirBadgeSize,
                    )
                }
            }
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                StratumText(
                    text = stringResource(UiR.string.lift_info_estimated_one_rep_max),
                    style = StratumThemeAccessor.typography.sectionLabel,
                    muted = true,
                )
                StratumNumber(
                    text = personalRecord.e1rmText,
                    style =
                        StratumThemeAccessor.typography.titleLarge.copy(
                            color = colors.prHighlight,
                        ),
                )
            }
        }
    }
}

private val HistoryPrRirBadgeSize = 20.dp

@Composable
private fun HistorySessionRow(
    session: LiftHistorySessionUi,
    onToggle: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                StratumText(text = session.dateText, muted = true)
                StratumText(text = session.workoutLabel)
                StratumText(text = session.bestSetText, muted = true)
            }
            Icon(
                painter =
                    painterResource(
                        if (session.expanded) {
                            UiR.drawable.ic_chevron_down
                        } else {
                            UiR.drawable.ic_chevron_right
                        },
                    ),
                contentDescription =
                    stringResource(
                        if (session.expanded) {
                            UiR.string.lift_info_collapse_session
                        } else {
                            UiR.string.lift_info_expand_session
                        },
                    ),
                tint = StratumThemeAccessor.colors.textMuted,
            )
        }
        AnimatedVisibility(visible = session.expanded) {
            Column(
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                session.sets.forEach { set ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StratumText(
                            text =
                                stringResource(
                                    UiR.string.lift_info_set_row,
                                    set.setIndex,
                                ) + "  " + set.weightRepsText,
                        )
                        if (set.rir != null) {
                            RirTargetBadge(
                                rirGoal = set.rir,
                                onClick = {},
                                enabled = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun allMuscleTiers(): LiftSwapMuscleTiers {
    val upper =
        listOf(
            MuscleGroup.Chest,
            MuscleGroup.Back,
            MuscleGroup.AnteriorDeltoid,
            MuscleGroup.LateralDeltoid,
            MuscleGroup.PosteriorDeltoid,
            MuscleGroup.Tricep,
            MuscleGroup.Bicep,
            MuscleGroup.Trap,
            MuscleGroup.Forearm,
        )
    val lower =
        listOf(
            MuscleGroup.Quad,
            MuscleGroup.Hamstring,
            MuscleGroup.Glute,
            MuscleGroup.Calf,
            MuscleGroup.Abductor,
            MuscleGroup.Adductor,
        )
    return LiftSwapMuscleTiers(
        upper = upper.map { StratumMuscleFilterCardState(it, it.toVolumeDisplayName()) },
        lower = lower.map { StratumMuscleFilterCardState(it, it.toVolumeDisplayName()) },
    )
}

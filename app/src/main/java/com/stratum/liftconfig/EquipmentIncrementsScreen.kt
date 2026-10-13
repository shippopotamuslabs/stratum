package com.stratum.liftconfig

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.workout.ui.ProposalEditContext
import com.stratum.feature.workout.ui.ProposalTierArgs

/**
 * Band-strip equipment increment editor (STR-58, Option A).
 *
 * Renders the unified [IncrementEditorScreen]; this route only maps its
 * UiState to [IncrementEditorChrome] plus callbacks. Tiers render as a
 * segmented bar; tapping a band opens a bottom-sheet editor with only Ends-at
 * + Step (starts are derived, the last band is always open).
 */
@Composable
fun EquipmentIncrementsRoute(
    equipmentId: String?,
    onBackClick: () -> Unit,
    viewModel: EquipmentIncrementsViewModel = hiltViewModel(),
    /** Catalog lift that opened this editor (Save As New reassigns it to the copy). */
    sourceLiftId: String? = null,
    initialTiersLb: List<ProposalTierArgs> = emptyList(),
    proposalContext: ProposalEditContext? = null,
    onNavigateToLift: (liftId: String) -> Unit = {},
    onChooseEquipmentForLift: (liftId: String) -> Unit = {},
) {
    LaunchedEffect(equipmentId, sourceLiftId, initialTiersLb, proposalContext) {
        viewModel.initialize(equipmentId, sourceLiftId, initialTiersLb, proposalContext)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.deleteDialog.collectAsStateWithLifecycle()
    LaunchedEffect(state.editor.saveSucceeded, state.deleteSucceeded) {
        if (state.editor.saveSucceeded || state.deleteSucceeded) onBackClick()
    }
    LaunchedEffect(viewModel) {
        viewModel.swapNavRequests.collect { onChooseEquipmentForLift(it) }
    }
    val scanPicker =
        rememberScanPickerState(
            scanUi = state.editor.scanUi,
            onScanPhoto = viewModel::scan,
        )
    // Null = band editor sheet closed; non-null opens it for that band.
    var selectedBand by rememberSaveable { mutableStateOf<Int?>(null) }
    var showAffectedSheet by rememberSaveable { mutableStateOf(false) }
    var showSaveConfirm by rememberSaveable { mutableStateOf(false) }
    var showForkSheet by rememberSaveable { mutableStateOf(false) }
    var showStopsSheet by rememberSaveable { mutableStateOf(false) }
    val tiers = state.editor.tiers
    val selection = selectedBand?.coerceIn(0, (tiers.size - 1).coerceAtLeast(0))

    val affectedCount = state.affectedLiftCount
    IncrementEditorScreen(
        editor = state.editor,
        chrome =
            IncrementEditorChrome(
                contextText =
                    stringResource(
                        UiR.string.equipment_shared_by,
                        affectedCount,
                    ),
                onContextClick = if (affectedCount > 0) ({ showAffectedSheet = true }) else null,
                saveText = stringResource(UiR.string.equipment_save_changes),
                saveConfirm =
                    IncrementSaveConfirm(
                        title = stringResource(UiR.string.equipment_save_title),
                        body = stringResource(UiR.string.equipment_save_body, affectedCount),
                        confirmLabel =
                            if (affectedCount > 0) {
                                stringResource(UiR.string.equipment_save_confirm, affectedCount)
                            } else {
                                stringResource(UiR.string.equipment_save_changes)
                            },
                        dismissLabel = stringResource(UiR.string.equipment_dialog_cancel),
                        onConfirm = {
                            showSaveConfirm = false
                            viewModel.save()
                        },
                        onDismiss = { showSaveConfirm = false },
                    ),
                forkSuggestion = "${state.equipmentName.ifBlank { "Equipment" }} (copy)",
            ),
        title =
            state.equipmentName.ifBlank {
                stringResource(UiR.string.equipment_increments_title)
            },
        selection = selection,
        scanPicker = scanPicker,
        bandActions =
            IncrementEditorBandActions(
                onBandClick = { selectedBand = it },
                onAddBand = {
                    // Appends one open band; the sheet opens on the new last band.
                    selectedBand = tiers.size
                    viewModel.addTier()
                },
                onBandSheetDismiss = { selectedBand = null },
                onBandFieldChanged = viewModel::onBandFieldChanged,
                onDeleteBand = {
                    selectedBand = null
                    viewModel.removeTier(it)
                },
            ),
        stopActions =
            IncrementEditorStopActions(
                onStopsClick = { showStopsSheet = true },
                onStopChanged = viewModel::onMicroChanged,
                onAddStop = viewModel::onAddMicro,
                onRemoveStop = viewModel::onRemoveMicro,
                onStopsDismiss = { showStopsSheet = false },
            ),
        saveActions =
            IncrementEditorSaveActions(
                onSaveClick = { showSaveConfirm = true },
                onSaveAsNewClick = { showForkSheet = true },
                onForkCreate = { name ->
                    showForkSheet = false
                    viewModel.saveAsNew(name)
                },
                onForkDismiss = { showForkSheet = false },
            ),
        deleteAction =
            if (state.canDelete) {
                IncrementEditorDeleteAction(
                    text = stringResource(UiR.string.equipment_delete_button),
                    onClick = viewModel::onStartDelete,
                )
            } else {
                null
            },
        onBackClick = onBackClick,
        showSaveConfirm = showSaveConfirm,
        showForkSheet = showForkSheet,
        showStopsSheet = showStopsSheet,
    )
    if (pendingDelete != null) {
        val pending = pendingDelete!!
        EquipmentDeleteConfirmDialog(
            name = pending.name,
            onConfirm = viewModel::deleteEquipment,
            onDismiss = viewModel::onDismissDelete,
            affectedLiftNames = pending.affectedLiftNames,
            affectedWorkouts = pending.affectedWorkouts,
            candidates = pending.candidates,
            selectedReplacementId = pending.selectedReplacementId,
            usesBodyweightFallback = pending.usesBodyweightFallback,
            canConfirmDelete = pending.canConfirmDelete,
            onSelectReplacement = viewModel::onSelectDeleteReplacement,
        )
    }
    if (showAffectedSheet) {
        AffectedLiftsSheet(
            lifts = state.affectedLifts,
            onDismiss = { showAffectedSheet = false },
            onLiftClick = { liftId ->
                showAffectedSheet = false
                onNavigateToLift(liftId)
            },
            onSwapClick = { liftId ->
                showAffectedSheet = false
                viewModel.requestSwapForLift(liftId)
            },
        )
    }
}

@Composable
private fun AffectedLiftsSheet(
    lifts: List<AffectedLiftUi>,
    onDismiss: () -> Unit,
    onLiftClick: (String) -> Unit,
    onSwapClick: (String) -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    StratumBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(UiR.string.equipment_affected_sheet_title),
    ) {
        lifts.forEach { lift ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onLiftClick(lift.id) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StratumText(
                    text = lift.name,
                    style = StratumThemeAccessor.typography.bodyLarge,
                    muted = !lift.enabled,
                    modifier = Modifier.weight(1f),
                )
                if (!lift.enabled) {
                    StratumText(
                        text = stringResource(UiR.string.equipment_affected_disabled),
                        style = StratumThemeAccessor.typography.sectionLabel,
                        muted = true,
                    )
                }
                StratumIconButton(onClick = { onSwapClick(lift.id) }) {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_swap_horiz),
                        contentDescription =
                            stringResource(UiR.string.equipment_affected_swap),
                        tint = colors.textMuted,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

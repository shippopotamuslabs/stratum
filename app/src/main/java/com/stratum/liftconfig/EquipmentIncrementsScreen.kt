package com.stratum.liftconfig

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumConfirmDialog
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextButton
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.workout.ui.ProposalEditContext
import com.stratum.feature.workout.ui.ProposalTierArgs

/**
 * Band-strip equipment increment editor (STR-58, Option A; shared components
 * in [IncrementsBandEditor.kt] since STR-61 slice 1).
 *
 * Tiers render as a segmented bar; tapping a band opens a bottom-sheet editor
 * with only Ends-at + Step (starts are derived, the last band is always open).
 */
@Composable
fun EquipmentIncrementsRoute(
    equipmentId: String,
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
    LaunchedEffect(state.editor.saveSucceeded) {
        if (state.editor.saveSucceeded) onBackClick()
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

    EquipmentBandEditorScreen(
        state = state,
        title =
            state.equipmentName.ifBlank {
                stringResource(UiR.string.equipment_increments_title)
            },
        selection = selection,
        scanPicker = scanPicker,
        showAffected = state.affectedLiftCount > 0,
        onBandClick = { selectedBand = it },
        onAddBand = {
            // Appends one open band; the sheet opens on the new last band.
            selectedBand = tiers.size
            viewModel.addTier()
        },
        onBandSheetDismiss = { selectedBand = null },
        onBandEndChanged = viewModel::onBandEndChanged,
        onBandStepChanged = viewModel::onTierIncrementChanged,
        onDeleteBand = {
            selectedBand = null
            viewModel.removeTier(it)
        },
        onSharedClick = { showAffectedSheet = true },
        onStopsClick = { showStopsSheet = true },
        onSaveClick = { showSaveConfirm = true },
        onSaveAsNewClick = { showForkSheet = true },
        onBackClick = onBackClick,
        onStopChanged = viewModel::onMicroChanged,
        onAddStop = viewModel::onAddMicro,
        onRemoveStop = viewModel::onRemoveMicro,
        onSaveConfirm = {
            showSaveConfirm = false
            viewModel.save()
        },
        onSaveDismiss = { showSaveConfirm = false },
        onForkCreate = { name ->
            showForkSheet = false
            viewModel.saveAsNew(name)
        },
        onForkDismiss = { showForkSheet = false },
        onStopsDismiss = { showStopsSheet = false },
        showSaveConfirm = showSaveConfirm,
        showForkSheet = showForkSheet,
        showStopsSheet = showStopsSheet,
    )
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

@Suppress("LongParameterList")
@Composable
internal fun EquipmentBandEditorScreen(
    state: EquipmentIncrementsUiState,
    title: String,
    selection: Int?,
    scanPicker: ScanPicker,
    showAffected: Boolean,
    onBandClick: (Int) -> Unit,
    onAddBand: () -> Unit,
    onBandSheetDismiss: () -> Unit,
    onBandEndChanged: (Int, String) -> Unit,
    onBandStepChanged: (Int, String) -> Unit,
    onDeleteBand: (Int) -> Unit,
    onSharedClick: () -> Unit,
    onStopsClick: () -> Unit,
    onSaveClick: () -> Unit,
    onSaveAsNewClick: () -> Unit,
    onBackClick: () -> Unit,
    onStopChanged: (Int, String) -> Unit,
    onAddStop: () -> Unit,
    onRemoveStop: (Int) -> Unit,
    onSaveConfirm: () -> Unit,
    onSaveDismiss: () -> Unit,
    onForkCreate: (String) -> Unit,
    onForkDismiss: () -> Unit,
    onStopsDismiss: () -> Unit,
    showSaveConfirm: Boolean,
    showForkSheet: Boolean,
    showStopsSheet: Boolean,
    modifier: Modifier = Modifier,
) {
    val editor = state.editor
    StratumScaffold(
        modifier = modifier,
        topBar = {
            StratumTopBar(
                title = title,
                navigationIcon = {
                    StratumTopBarBackButton(
                        onClick = onBackClick,
                        contentDescription = stringResource(UiR.string.lift_config_navigate_back),
                    )
                },
                actions = {
                    if (editor.scanAvailable) {
                        ScanIconEntry(picker = scanPicker, scanUi = editor.scanUi)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            EditorContextRow(
                text =
                    if (state.affectedLiftCount > 0) {
                        stringResource(
                            UiR.string.equipment_shared_by,
                            state.affectedLiftCount,
                        )
                    } else {
                        stringResource(UiR.string.equipment_increments_affects_none)
                    },
                onClick = if (showAffected) onSharedClick else null,
            )
            StratumText(
                text = stringResource(UiR.string.equipment_band_section),
                style = StratumThemeAccessor.typography.sectionLabel,
                muted = true,
                modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
            )
            StratumText(
                text = stringResource(UiR.string.equipment_band_hint),
                style = StratumThemeAccessor.typography.sectionLabel,
                muted = true,
                modifier = Modifier.padding(start = 2.dp, bottom = 10.dp),
            )
            BandStrip(
                tiers = editor.tiers,
                unitLabel = editor.unitLabel,
                selection = selection,
                onBandClick = onBandClick,
                onAddBand = onAddBand,
            )
            if (selection != null && editor.tiers.indices.contains(selection)) {
                val start = IncrementBandTiling.startAt(editor.tiers, selection)
                val isOpen = selection == editor.tiers.lastIndex
                StratumText(
                    text =
                        if (isOpen) {
                            stringResource(
                                UiR.string.equipment_band_caption_open,
                                selection + 1,
                                editor.tiers.size,
                                start,
                                editor.unitLabel,
                            )
                        } else {
                            stringResource(
                                UiR.string.equipment_band_caption,
                                selection + 1,
                                editor.tiers.size,
                                start,
                                editor.unitLabel,
                            )
                        },
                    style = StratumThemeAccessor.typography.sectionLabel,
                    muted = true,
                    modifier = Modifier.padding(start = 2.dp, top = 6.dp),
                )
            }
            if (editor.scanAvailable) {
                ScanStatusLines(
                    scanUi = editor.scanUi,
                    picker = scanPicker,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (editor.validationMessage != null) {
                EditorErrorBanner(
                    message = editor.validationMessage,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (editor.warnedTierIndexes.isNotEmpty()) {
                StratumText(
                    text = stringResource(UiR.string.lift_increments_sample_warning),
                    style = StratumThemeAccessor.typography.sectionLabel,
                    muted = true,
                    modifier = Modifier.padding(start = 2.dp, top = 12.dp),
                )
            }
            StratumText(
                text = stringResource(UiR.string.lift_increments_micro_title),
                style = StratumThemeAccessor.typography.sectionLabel,
                muted = true,
                modifier = Modifier.padding(start = 2.dp, top = 14.dp, bottom = 8.dp),
            )
            MicroStopsRow(
                stops = editor.microStops,
                unitLabel = editor.unitLabel,
                onClick = onStopsClick,
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StratumButton(
                    text = stringResource(UiR.string.equipment_save_changes),
                    onClick = onSaveClick,
                    enabled = !editor.isSaving && editor.validationMessage == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                StratumTextButton(
                    text = stringResource(UiR.string.equipment_increments_save_as_new),
                    onClick = onSaveAsNewClick,
                    enabled = !editor.isSaving && editor.validationMessage == null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (selection != null && editor.tiers.indices.contains(selection)) {
        BandEditorSheet(
            index = selection,
            tier = editor.tiers[selection],
            start = IncrementBandTiling.startAt(editor.tiers, selection),
            isLast = selection == editor.tiers.lastIndex,
            canDelete = editor.tiers.size > 1,
            unitLabel = editor.unitLabel,
            onEndChanged = { onBandEndChanged(selection, it) },
            onStepChanged = { onBandStepChanged(selection, it) },
            onDelete = { onDeleteBand(selection) },
            onDone = onBandSheetDismiss,
            onDismiss = onBandSheetDismiss,
        )
    }
    if (showForkSheet) {
        ForkNameSheet(
            suggestion = "${state.equipmentName.ifBlank { "Equipment" }} (copy)",
            onCreate = onForkCreate,
            onDismiss = onForkDismiss,
        )
    }
    if (showStopsSheet) {
        StopsSheet(
            stops = editor.microStops,
            unitLabel = editor.unitLabel,
            onStopChanged = onStopChanged,
            onAddStop = onAddStop,
            onRemoveStop = onRemoveStop,
            onDismiss = onStopsDismiss,
        )
    }
    if (showSaveConfirm) {
        val count = state.affectedLiftCount
        StratumConfirmDialog(
            title = stringResource(UiR.string.equipment_save_title),
            body = stringResource(UiR.string.equipment_save_body, count),
            confirmLabel =
                if (count > 0) {
                    stringResource(UiR.string.equipment_save_confirm, count)
                } else {
                    stringResource(UiR.string.equipment_save_changes)
                },
            dismissLabel = stringResource(UiR.string.equipment_dialog_cancel),
            onConfirm = onSaveConfirm,
            onDismiss = onSaveDismiss,
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

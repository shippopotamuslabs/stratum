package com.stratum.liftconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextButton
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.workout.ui.ProposalEditContext
import com.stratum.feature.workout.ui.ProposalTierArgs

/**
 * Per-lift override increments editor (STR-61 slice 3).
 *
 * Same shared band-strip editor as the equipment route (see
 * [IncrementsBandEditor.kt]); only the chrome differs for override
 * semantics: an inheritance context row, reset-to-equipment, a direct Save
 * (one lift affected — no blast-radius confirm), and Save-as-new only when
 * the lift has equipment to fork from.
 */
@Composable
fun LiftIncrementsRoute(
    liftId: String,
    onBackClick: () -> Unit,
    viewModel: LiftIncrementsViewModel = hiltViewModel(),
    initialTiersLb: List<ProposalTierArgs> = emptyList(),
    proposalContext: ProposalEditContext? = null,
) {
    LaunchedEffect(liftId, initialTiersLb, proposalContext) {
        viewModel.initialize(liftId, initialTiersLb, proposalContext)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.editor.saveSucceeded) {
        if (state.editor.saveSucceeded) onBackClick()
    }
    val scanPicker =
        rememberScanPickerState(
            scanUi = state.editor.scanUi,
            onScanPhoto = viewModel::scan,
        )
    // Null = band editor sheet closed; non-null opens it for that band.
    var selectedBand by rememberSaveable { mutableStateOf<Int?>(null) }
    var showForkSheet by rememberSaveable { mutableStateOf(false) }
    var showStopsSheet by rememberSaveable { mutableStateOf(false) }
    val tiers = state.editor.tiers
    val selection = selectedBand?.coerceIn(0, (tiers.size - 1).coerceAtLeast(0))

    LiftBandEditorScreen(
        state = state,
        title =
            state.liftName.ifBlank {
                stringResource(UiR.string.lift_increments_title)
            },
        selection = selection,
        scanPicker = scanPicker,
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
        onStopsClick = { showStopsSheet = true },
        onSaveAsNewClick = { showForkSheet = true },
        onBackClick = onBackClick,
        onSave = viewModel::save,
        onResetToEquipment = viewModel::resetToEquipmentDefaults,
        onStopChanged = viewModel::onMicroChanged,
        onAddStop = viewModel::onAddMicro,
        onRemoveStop = viewModel::onRemoveMicro,
        onForkCreate = { name ->
            showForkSheet = false
            viewModel.saveAsNew(name)
        },
        onForkDismiss = { showForkSheet = false },
        onStopsDismiss = { showStopsSheet = false },
        showForkSheet = showForkSheet,
        showStopsSheet = showStopsSheet,
    )
}

@Suppress("LongParameterList")
@Composable
internal fun LiftBandEditorScreen(
    state: LiftIncrementsUiState,
    title: String,
    selection: Int?,
    scanPicker: ScanPicker,
    onBandClick: (Int) -> Unit,
    onAddBand: () -> Unit,
    onBandSheetDismiss: () -> Unit,
    onBandEndChanged: (Int, String) -> Unit,
    onBandStepChanged: (Int, String) -> Unit,
    onDeleteBand: (Int) -> Unit,
    onStopsClick: () -> Unit,
    onSaveAsNewClick: () -> Unit,
    onBackClick: () -> Unit,
    onSave: () -> Unit,
    onResetToEquipment: () -> Unit,
    onStopChanged: (Int, String) -> Unit,
    onAddStop: () -> Unit,
    onRemoveStop: (Int) -> Unit,
    onForkCreate: (String) -> Unit,
    onForkDismiss: () -> Unit,
    onStopsDismiss: () -> Unit,
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
                    state.inheritanceBanner
                        ?: stringResource(UiR.string.lift_increments_override_banner),
                onClick = null,
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
                BandCaption(
                    tiers = editor.tiers,
                    selection = selection,
                    unitLabel = editor.unitLabel,
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
                if (state.showResetToEquipment) {
                    StratumOutlinedButton(
                        text = stringResource(UiR.string.lift_increments_reset_to_equipment),
                        onClick = onResetToEquipment,
                        enabled = !editor.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                StratumButton(
                    text = stringResource(UiR.string.lift_increments_save),
                    onClick = onSave,
                    enabled = !editor.isSaving && editor.validationMessage == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.forkSourceId != null) {
                    StratumTextButton(
                        text = stringResource(UiR.string.equipment_increments_save_as_new),
                        onClick = onSaveAsNewClick,
                        enabled = !editor.isSaving && editor.validationMessage == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
    if (showForkSheet && state.forkSourceId != null) {
        ForkNameSheet(
            suggestion = state.forkSuggestion,
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
}

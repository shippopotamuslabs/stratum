package com.stratum.liftconfig

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.R as UiR
import com.stratum.feature.workout.ui.ProposalEditContext
import com.stratum.feature.workout.ui.ProposalTierArgs

/**
 * Per-lift override increments editor (STR-61 slice 3; unified screen since STR-73).
 *
 * Renders the same [IncrementEditorScreen] as the equipment route; only the
 * chrome differs for override semantics: an inheritance context row,
 * reset-to-equipment, a direct Save (one lift affected — no blast-radius
 * confirm), and Save-as-new only when the lift has equipment to fork from.
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

    IncrementEditorScreen(
        editor = state.editor,
        chrome =
            IncrementEditorChrome(
                contextText =
                    state.inheritanceBanner
                        ?: stringResource(UiR.string.lift_increments_override_banner),
                onContextClick = null,
                saveText = stringResource(UiR.string.lift_increments_save),
                saveConfirm = null,
                resetAction =
                    if (state.showResetToEquipment) {
                        viewModel::resetToEquipmentDefaults
                    } else {
                        null
                    },
                forkSuggestion = state.forkSourceId?.let { state.forkSuggestion },
            ),
        title =
            state.liftName.ifBlank {
                stringResource(UiR.string.lift_increments_title)
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
                onBandEndChanged = viewModel::onBandEndChanged,
                onBandStepChanged = viewModel::onTierIncrementChanged,
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
                onSaveClick = viewModel::save,
                onSaveAsNewClick = { showForkSheet = true },
                onForkCreate = { name ->
                    showForkSheet = false
                    viewModel.saveAsNew(name)
                },
                onForkDismiss = { showForkSheet = false },
            ),
        onBackClick = onBackClick,
        showSaveConfirm = false,
        showForkSheet = showForkSheet,
        showStopsSheet = showStopsSheet,
    )
}

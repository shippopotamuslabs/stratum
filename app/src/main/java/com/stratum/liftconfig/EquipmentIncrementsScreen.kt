package com.stratum.liftconfig

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.R as UiR

@Composable
fun EquipmentIncrementsRoute(
    equipmentId: String,
    onBackClick: () -> Unit,
    viewModel: EquipmentIncrementsViewModel = hiltViewModel(),
) {
    LaunchedEffect(equipmentId) {
        viewModel.initialize(equipmentId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saveSucceeded) {
        if (state.saveSucceeded) onBackClick()
    }
    // Reuse the lift increments editor shell with equipment title/save.
    LiftIncrementsScreen(
        state =
            LiftIncrementsUiState(
                liftId = state.equipmentId,
                liftName =
                    state.equipmentName.ifBlank {
                        stringResource(UiR.string.equipment_increments_title)
                    },
                tiers = state.tiers,
                validationMessage = state.validationMessage,
                isSaving = state.isSaving,
                saveSucceeded = state.saveSucceeded,
                inheritanceBanner = null,
                showResetToEquipment = false,
            ),
        onBackClick = onBackClick,
        onTierLowerChanged = viewModel::onTierLowerChanged,
        onTierUpperChanged = viewModel::onTierUpperChanged,
        onTierIncrementChanged = viewModel::onTierIncrementChanged,
        onOpenEndedToggled = viewModel::onOpenEndedToggled,
        onAddTier = viewModel::addTier,
        onRemoveTier = viewModel::removeTier,
        onSave = viewModel::save,
        onResetToEquipment = {},
        modifier = Modifier,
    )
}

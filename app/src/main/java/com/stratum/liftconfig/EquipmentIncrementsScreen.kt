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
                affectsBanner = formatAffectsBanner(state),
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

/**
 * Presentation mapping for "Affects N lifts" + first 3 names + "+M more".
 * Domain models stay out of the leaf editor; only strings enter [LiftIncrementsScreen].
 */
@Composable
internal fun formatAffectsBanner(state: EquipmentIncrementsUiState): String {
    val count = state.affectedLiftCount
    if (count <= 0) {
        return stringResource(UiR.string.equipment_increments_affects_none)
    }
    val names = state.affectedLiftNames
    if (names.isEmpty()) {
        return stringResource(UiR.string.equipment_increments_affects_count, count)
    }
    val preview = names.take(AFFECTS_NAME_PREVIEW_LIMIT).joinToString(", ")
    val more = count - minOf(AFFECTS_NAME_PREVIEW_LIMIT, names.size)
    return if (more > 0) {
        stringResource(
            UiR.string.equipment_increments_affects_with_more,
            count,
            preview,
            more,
        )
    } else {
        stringResource(
            UiR.string.equipment_increments_affects_with_names,
            count,
            preview,
        )
    }
}

private const val AFFECTS_NAME_PREVIEW_LIMIT = 3

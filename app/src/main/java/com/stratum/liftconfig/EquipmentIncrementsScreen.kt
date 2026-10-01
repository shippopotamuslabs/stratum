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
    var showAffectedSheet by rememberSaveable { mutableStateOf(false) }
    val affectsClick =
        if (state.affectedLiftCount > 0) {
            { showAffectedSheet = true }
        } else {
            null
        }
    // Reuse the lift increments editor shell with equipment title/save.
    LiftIncrementsScreen(
        state =
            state.toLiftIncrementsUiState(
                fallbackTitle = stringResource(UiR.string.equipment_increments_title),
            ),
        onBackClick = onBackClick,
        onTierLowerChanged = viewModel::onTierLowerChanged,
        onTierUpperChanged = viewModel::onTierUpperChanged,
        onTierIncrementChanged = viewModel::onTierIncrementChanged,
        onOpenEndedToggled = viewModel::onOpenEndedToggled,
        onAddTier = viewModel::addTier,
        onRemoveTier = viewModel::removeTier,
        onSave = viewModel::save,
        microActions =
            MicroEditorActions(
                onStopChanged = viewModel::onMicroChanged,
                onAddStop = viewModel::onAddMicro,
                onRemoveStop = viewModel::onRemoveMicro,
            ),
        onResetToEquipment = {},
        onScanPhoto = viewModel::scan,
        forkUi =
            ForkSaveUi(
                onSaveAsNew = viewModel::saveAsNew,
                affectsBanner =
                    AffectsBannerUi(
                        message = formatAffectsBanner(state.affectedLiftCount, state.affectedLiftNames),
                        onClick = affectsClick,
                    ),
            ),
        modifier = Modifier,
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

/**
 * Maps equipment editor state onto the shared increments shell. The shared
 * [IncrementsEditorData] passes through untouched, so new shell fields flow
 * without mapper edits; only equipment-specific presets (title, no lift
 * banners) are applied here.
 */
internal fun EquipmentIncrementsUiState.toLiftIncrementsUiState(
    fallbackTitle: String,
): LiftIncrementsUiState =
    LiftIncrementsUiState(
        liftId = equipmentId,
        liftName = equipmentName.ifBlank { fallbackTitle },
        editor = editor,
        inheritanceBanner = null,
        showResetToEquipment = false,
    )

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

/**
 * Presentation mapping for "Affects N lifts" + first 3 names + "+M more".
 * Domain models stay out of the leaf editor; only strings enter [LiftIncrementsScreen].
 */
@Composable
internal fun formatAffectsBanner(count: Int, names: List<String>): String {
    if (count <= 0) {
        return stringResource(UiR.string.equipment_increments_affects_none)
    }
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

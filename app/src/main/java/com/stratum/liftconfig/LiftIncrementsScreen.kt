package com.stratum.liftconfig

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumSwitch
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextField
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.theme.StratumThemeAccessor

@Composable
fun LiftIncrementsRoute(
    liftId: String,
    onBackClick: () -> Unit,
    viewModel: LiftIncrementsViewModel = hiltViewModel(),
) {
    LaunchedEffect(liftId) {
        viewModel.initialize(liftId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saveSucceeded) {
        if (state.saveSucceeded) onBackClick()
    }
    LiftIncrementsScreen(
        state = state,
        onBackClick = onBackClick,
        onTierLowerChanged = viewModel::onTierLowerChanged,
        onTierUpperChanged = viewModel::onTierUpperChanged,
        onTierIncrementChanged = viewModel::onTierIncrementChanged,
        onOpenEndedToggled = viewModel::onOpenEndedToggled,
        onAddTier = viewModel::addTier,
        onRemoveTier = viewModel::removeTier,
        onSave = viewModel::save,
        onResetToEquipment = viewModel::resetToEquipmentDefaults,
    )
}

@Composable
fun LiftIncrementsScreen(
    state: LiftIncrementsUiState,
    onBackClick: () -> Unit,
    onTierLowerChanged: (Int, String) -> Unit,
    onTierUpperChanged: (Int, String) -> Unit,
    onTierIncrementChanged: (Int, String) -> Unit,
    onOpenEndedToggled: (Int, Boolean) -> Unit,
    onAddTier: () -> Unit,
    onRemoveTier: (Int) -> Unit,
    onSave: () -> Unit,
    onResetToEquipment: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    StratumScaffold(
        modifier = modifier,
        topBar = {
            StratumTopBar(
                title = state.liftName.ifBlank { stringResource(UiR.string.lift_increments_title) },
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
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
            ) {
                item {
                    IncrementsHelpCard()
                }
                if (state.affectsBanner != null) {
                    item {
                        InheritanceBanner(message = state.affectsBanner)
                    }
                }
                if (state.inheritanceBanner != null) {
                    item {
                        InheritanceBanner(message = state.inheritanceBanner)
                    }
                } else if (state.showResetToEquipment) {
                    item {
                        InheritanceBanner(
                            message = stringResource(UiR.string.lift_increments_override_banner),
                        )
                    }
                }
                if (state.validationMessage != null) {
                    item {
                        IncrementsValidationBanner(message = state.validationMessage)
                    }
                }
                itemsIndexed(state.tiers, key = { index, _ -> index }) { index, tier ->
                    TierEditorCard(
                        index = index,
                        tier = tier,
                        isLast = index == state.tiers.lastIndex,
                        canRemove = state.tiers.size > 1,
                        onLowerChanged = { onTierLowerChanged(index, it) },
                        onUpperChanged = { onTierUpperChanged(index, it) },
                        onIncrementChanged = { onTierIncrementChanged(index, it) },
                        onOpenEndedToggled = { onOpenEndedToggled(index, it) },
                        onRemove = { onRemoveTier(index) },
                    )
                }
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StratumOutlinedButton(
                    text = stringResource(UiR.string.lift_increments_add_tier),
                    onClick = onAddTier,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.showResetToEquipment) {
                    StratumOutlinedButton(
                        text = stringResource(UiR.string.lift_increments_reset_to_equipment),
                        onClick = onResetToEquipment,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                StratumButton(
                    text = stringResource(UiR.string.lift_increments_save),
                    onClick = onSave,
                    enabled = !state.isSaving && state.validationMessage == null,
                    modifier = Modifier.padding(bottom = 20.dp).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun InheritanceBanner(message: String) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    StratumText(
        text = message,
        style = StratumThemeAccessor.typography.sectionLabel,
        muted = true,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(
                    width = shapes.borderWidth,
                    color = colors.accent,
                    shape = shapes.panel,
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

@Composable
private fun IncrementsHelpCard() {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(
                    width = shapes.borderWidth,
                    color = colors.borderSubtle,
                    shape = shapes.panel,
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_info),
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(18.dp),
        )
        StratumText(
            text = stringResource(UiR.string.lift_increments_help),
            style = StratumThemeAccessor.typography.sectionLabel,
            muted = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun IncrementsValidationBanner(message: String) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    StratumText(
        text = message,
        style =
            StratumThemeAccessor.typography.bodyLarge.copy(
                color = colors.error,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(
                    width = shapes.borderWidth,
                    color = colors.error,
                    shape = shapes.panel,
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

@Composable
private fun TierEditorCard(
    index: Int,
    tier: IncrementTierDraft,
    isLast: Boolean,
    canRemove: Boolean,
    onLowerChanged: (String) -> Unit,
    onUpperChanged: (String) -> Unit,
    onIncrementChanged: (String) -> Unit,
    onOpenEndedToggled: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val summary = tierSummaryText(tier)

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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                StratumText(
                    text = stringResource(UiR.string.lift_increments_tier_label, index + 1),
                    style = StratumThemeAccessor.typography.titleMedium,
                )
                if (summary != null) {
                    StratumNumber(
                        text = summary,
                        style = StratumThemeAccessor.typography.sectionLabel,
                    )
                }
            }
            if (canRemove) {
                StratumIconButton(onClick = onRemove) {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_close),
                        contentDescription = stringResource(UiR.string.lift_increments_remove_tier),
                        tint = colors.textMuted,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StratumTextField(
                value = tier.lowerBound,
                onValueChange = onLowerChanged,
                label = stringResource(UiR.string.lift_increments_lower),
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            if (!tier.openEnded) {
                StratumTextField(
                    value = tier.upperBound.orEmpty(),
                    onValueChange = onUpperChanged,
                    label = stringResource(UiR.string.lift_increments_upper),
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            } else {
                StratumTextField(
                    value = stringResource(UiR.string.lift_increments_upper_open),
                    onValueChange = {},
                    label = stringResource(UiR.string.lift_increments_upper),
                    modifier = Modifier.weight(1f),
                    enabled = false,
                )
            }
            StratumTextField(
                value = tier.increment,
                onValueChange = onIncrementChanged,
                label = stringResource(UiR.string.lift_increments_step),
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }

        if (isLast) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(shapes.borderWidth)
                        .background(colors.borderSubtle),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StratumText(
                    text = stringResource(UiR.string.lift_increments_open_ended),
                    style = StratumThemeAccessor.typography.sectionLabel,
                    muted = true,
                )
                StratumSwitch(
                    checked = tier.openEnded,
                    onCheckedChange = onOpenEndedToggled,
                )
            }
        }
    }
}

@Composable
private fun tierSummaryText(tier: IncrementTierDraft): String? {
    val lower = tier.lowerBound.trim()
    val step = tier.increment.trim()
    if (lower.isEmpty() || step.isEmpty()) return null
    return if (tier.openEnded) {
        stringResource(UiR.string.lift_increments_summary_open, lower, step)
    } else {
        val upper = tier.upperBound?.trim().orEmpty()
        if (upper.isEmpty()) return null
        stringResource(UiR.string.lift_increments_summary_closed, lower, upper, step)
    }
}

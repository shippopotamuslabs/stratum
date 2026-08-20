package com.stratum.core.ui.component.platecalculator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumFilterChip
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextField
import com.stratum.core.ui.component.formatWeightAmount
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.domain.lift.BarPreset
import com.stratum.domain.lift.BarPresetKind
import com.stratum.domain.lift.PlateCalculator

/**
 * Expanded plate calculator. Seeded from the active set, then the user can type any target
 * or bar weight to preview plates. Bar/plate edits stay local until **Apply**; Done, close,
 * and swipe-dismiss discard them. System IME is hidden when the sheet closes so it does
 * not cover the custom set-log pad.
 */
@Composable
fun StratumPlateCalculatorSheet(
    initialTargetWeight: Double,
    unitLabel: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    isDoubleSided: Boolean = true,
    onApplyWeight: ((appliedWeight: Double, inventory: PlateInventoryUi) -> Unit)? = null,
    initialInventory: PlateInventoryUi = defaultPlateInventory(unitLabel),
) {
    val keyboard = LocalSoftwareKeyboardController.current
    DisposableEffect(Unit) {
        onDispose { keyboard?.hide() }
    }

    val isKg = unitLabel.equals("kg", ignoreCase = true)
    val unitSuffix = unitLabel.lowercase()
    val barPresets = remember(isKg) { PlateCalculator.barPresets(isKg) }
    val allPlateDenominations = remember(isKg) { PlateCalculator.allAvailablePlates(isKg) }

    var targetWeightText by remember(initialTargetWeight) {
        mutableStateOf(
            if (initialTargetWeight > 0.0) PlateColors.formatPlateWeight(initialTargetWeight) else "",
        )
    }
    var barWeightText by remember(initialInventory) {
        mutableStateOf(PlateColors.formatPlateWeight(initialInventory.barWeight))
    }
    var enabledPlates by remember(initialInventory) {
        mutableStateOf(initialInventory.enabledPlates)
    }

    val parsedTarget = parsePlateTargetWeight(targetWeightText, fallback = 0.0)
    val parsedBarWeight = parsePlateTargetWeight(barWeightText, fallback = 0.0)

    fun inventoryFromEdits(): PlateInventoryUi =
        PlateInventoryUi(
            barWeight = parsedBarWeight.takeIf { it > 0.0 } ?: initialInventory.barWeight,
            enabledPlates = enabledPlates,
            unitLabel = unitLabel,
        )

    val plateLoad by remember(parsedTarget, parsedBarWeight, enabledPlates, unitLabel, isDoubleSided) {
        derivedStateOf {
            computePlateLoadUi(
                targetWeight = parsedTarget,
                barWeight = parsedBarWeight,
                availablePlates = enabledPlates.toList(),
                unitLabel = unitSuffix,
                isDoubleSided = isDoubleSided,
            )
        }
    }

    val isTargetError = !plateLoad.isValid && targetWeightText.isNotBlank()
    val targetErrorText = when {
        !isTargetError -> null
        plateLoad.status == PlateLoadStatusUi.BelowBarWeight ->
            stringResource(
                CoreUiR.string.plate_calculator_below_bar_minimum,
                formatWeightAmount(parsedBarWeight).ifBlank { "0" },
                unitSuffix,
            )
        plateLoad.status == PlateLoadStatusUi.CannotMatchWithAvailablePlates ->
            stringResource(
                CoreUiR.string.plate_calculator_remainder,
                formatWeightAmount(plateLoad.remainder).ifBlank { "0" },
                unitSuffix,
            )
        else -> null
    }

    StratumBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    StratumText(
                        text = stringResource(CoreUiR.string.plate_calculator_title),
                        style = StratumThemeAccessor.typography.titleLarge,
                    )
                    StratumText(
                        text = stringResource(
                            CoreUiR.string.plate_calculator_unit,
                            unitSuffix.uppercase(),
                        ),
                        style = StratumThemeAccessor.typography.bodyLarge.copy(
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            color = StratumThemeAccessor.colors.textMuted,
                        ),
                    )
                }

                StratumIconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(36.dp),
                    containerColor = StratumThemeAccessor.colors.surfaceSunken,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(CoreUiR.string.plate_calculator_close),
                        tint = StratumThemeAccessor.colors.textMuted,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StratumTextField(
                    value = targetWeightText,
                    onValueChange = { targetWeightText = it },
                    label = stringResource(
                        CoreUiR.string.plate_calculator_target_weight_label,
                        unitSuffix,
                    ),
                    placeholder = stringResource(CoreUiR.string.plate_calculator_target_placeholder),
                    isError = isTargetError,
                    errorText = targetErrorText,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1.2f),
                )
                StratumTextField(
                    value = barWeightText,
                    onValueChange = { barWeightText = it },
                    label = stringResource(
                        CoreUiR.string.plate_calculator_bar_weight_label,
                        unitSuffix,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(0.8f),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StratumText(
                    text = stringResource(CoreUiR.string.plate_calculator_bar_weight_presets),
                    style = StratumThemeAccessor.typography.sectionLabel,
                    color = StratumThemeAccessor.colors.textMuted,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    barPresets.forEach { preset ->
                        StratumFilterChip(
                            label = barPresetLabel(preset, unitSuffix),
                            selected = parsedBarWeight == preset.weight,
                            onSelectedChange = {
                                barWeightText = PlateColors.formatPlateWeight(preset.weight)
                            },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StratumText(
                    text = stringResource(CoreUiR.string.plate_calculator_available_plates),
                    style = StratumThemeAccessor.typography.sectionLabel,
                    color = StratumThemeAccessor.colors.textMuted,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    allPlateDenominations.forEach { plateWeight ->
                        val isEnabled = plateWeight in enabledPlates
                        val plateColor = PlateColors.colorFor(plateWeight, isKg = isKg)
                        val weightStr = PlateColors.formatPlateWeight(plateWeight)

                        StratumFilterChip(
                            label = "$weightStr $unitSuffix",
                            selected = isEnabled,
                            onSelectedChange = { checked ->
                                enabledPlates = if (checked) {
                                    enabledPlates + plateWeight
                                } else {
                                    enabledPlates - plateWeight
                                }
                            },
                            trailingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(plateColor),
                                )
                            },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StratumText(
                    text = stringResource(CoreUiR.string.plate_calculator_barbell_diagram),
                    style = StratumThemeAccessor.typography.sectionLabel,
                    color = StratumThemeAccessor.colors.textMuted,
                )
                StratumPlateBarbellDiagram(
                    platesPerSide = if (plateLoad.isValid) plateLoad.platesPerSide else emptyList(),
                    isMini = false,
                    showPerSideBadge = true,
                )
            }

            PlateLoadBreakdownCard(plateLoad = plateLoad, unitSuffix = unitSuffix)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val applyWeight = onApplyWeight
                val canApply = applyWeight != null && plateLoad.isValid && plateLoad.totalWeight > 0.0
                if (canApply && applyWeight != null) {
                    StratumButton(
                        text = stringResource(
                            CoreUiR.string.plate_calculator_apply_to_set,
                            formatWeightAmount(plateLoad.totalWeight),
                            unitSuffix,
                        ),
                        onClick = {
                            applyWeight(plateLoad.totalWeight, inventoryFromEdits())
                            onDismissRequest()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                StratumOutlinedButton(
                    text = stringResource(CoreUiR.string.plate_calculator_done),
                    onClick = onDismissRequest,
                    modifier = if (canApply) Modifier.weight(0.5f) else Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun PlateLoadBreakdownCard(
    plateLoad: PlateLoadUi,
    unitSuffix: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(StratumThemeAccessor.shapes.panel)
            .background(StratumThemeAccessor.colors.surfaceSunken)
            .border(
                width = StratumThemeAccessor.shapes.borderWidth,
                color = if (plateLoad.isValid) {
                    StratumThemeAccessor.colors.borderSubtle
                } else {
                    StratumThemeAccessor.colors.error.copy(alpha = 0.5f)
                },
                shape = StratumThemeAccessor.shapes.panel,
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StratumText(
                text = stringResource(CoreUiR.string.plate_calculator_per_side_breakdown),
                style = StratumThemeAccessor.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                ),
            )
            StratumText(
                text = stringResource(CoreUiR.string.plate_calculator_each_side),
                style = StratumThemeAccessor.typography.sectionLabel.copy(
                    fontSize = 10.sp,
                    color = StratumThemeAccessor.colors.textMuted,
                ),
            )
        }

        when (plateLoad.status) {
            PlateLoadStatusUi.BelowBarWeight -> {
                StratumText(
                    text = stringResource(CoreUiR.string.plate_calculator_below_bar),
                    style = StratumThemeAccessor.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = StratumThemeAccessor.colors.error,
                    ),
                )
                StratumText(
                    text = stringResource(
                        CoreUiR.string.plate_calculator_below_bar_minimum,
                        formatWeightAmount(plateLoad.barWeight),
                        unitSuffix,
                    ),
                    style = StratumThemeAccessor.typography.bodyLarge.copy(
                        fontSize = 13.sp,
                        color = StratumThemeAccessor.colors.textMuted,
                    ),
                )
            }
            PlateLoadStatusUi.CannotMatchWithAvailablePlates -> {
                StratumText(
                    text = stringResource(CoreUiR.string.plate_calculator_cannot_match),
                    style = StratumThemeAccessor.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = StratumThemeAccessor.colors.error,
                    ),
                )
                StratumText(
                    text = stringResource(
                        CoreUiR.string.plate_calculator_remainder,
                        formatWeightAmount(plateLoad.remainder),
                        unitSuffix,
                    ),
                    style = StratumThemeAccessor.typography.bodyLarge.copy(
                        fontSize = 13.sp,
                        color = StratumThemeAccessor.colors.textMuted,
                    ),
                )
            }
            PlateLoadStatusUi.ExactMatch -> {
                if (plateLoad.platesPerSide.isEmpty()) {
                    StratumText(
                        text = if (plateLoad.totalWeight > 0.0) {
                            stringResource(
                                CoreUiR.string.plate_calculator_no_plates_needed,
                                formatWeightAmount(plateLoad.barWeight),
                                unitSuffix,
                            )
                        } else {
                            stringResource(CoreUiR.string.plate_calculator_no_plates_loaded, unitSuffix)
                        },
                        style = StratumThemeAccessor.typography.bodyLarge.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = StratumThemeAccessor.colors.textMuted,
                        ),
                    )
                } else {
                    plateLoad.platesPerSide.forEach { plate ->
                        PlateBreakdownRow(plate = plate, unitSuffix = unitSuffix)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(StratumThemeAccessor.colors.borderSubtle),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StratumText(
                text = stringResource(
                    CoreUiR.string.plate_calculator_bar_summary,
                    formatWeightAmount(plateLoad.barWeight).ifBlank { "0" },
                    unitSuffix,
                ),
                style = StratumThemeAccessor.typography.bodyLarge.copy(
                    fontSize = 13.sp,
                    color = StratumThemeAccessor.colors.textMuted,
                ),
            )
            StratumText(
                text = stringResource(
                    CoreUiR.string.plate_calculator_plates_summary,
                    formatWeightAmount(plateLoad.totalPlateWeight).ifBlank { "0" },
                    unitSuffix,
                ),
                style = StratumThemeAccessor.typography.bodyLarge.copy(
                    fontSize = 13.sp,
                    color = StratumThemeAccessor.colors.textMuted,
                ),
            )
            StratumNumber(
                text = stringResource(
                    CoreUiR.string.plate_calculator_total_summary,
                    formatWeightAmount(plateLoad.totalWeight).ifBlank { "0" },
                    unitSuffix,
                ),
                style = StratumThemeAccessor.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = StratumThemeAccessor.colors.accent,
                ),
            )
        }
    }
}

@Composable
private fun PlateBreakdownRow(
    plate: PlateItemUi,
    unitSuffix: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(plate.color),
            )
            StratumText(
                text = stringResource(
                    CoreUiR.string.plate_calculator_plate_item,
                    plate.label,
                    unitSuffix,
                ),
                style = StratumThemeAccessor.typography.bodyLarge.copy(
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                ),
            )
        }

        StratumNumber(
            text = "${plate.count}x",
            style = StratumThemeAccessor.typography.bodyLarge.copy(
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
                color = StratumThemeAccessor.colors.textPrimary,
            ),
        )
    }
}

@Composable
private fun barPresetLabel(preset: BarPreset, unitSuffix: String): String {
    val formatted = PlateColors.formatPlateWeight(preset.weight)
    return when (preset.kind) {
        BarPresetKind.Olympic ->
            stringResource(CoreUiR.string.plate_calculator_preset_olympic, formatted, unitSuffix)
        BarPresetKind.Womens ->
            stringResource(CoreUiR.string.plate_calculator_preset_womens, formatted, unitSuffix)
        BarPresetKind.Squat ->
            stringResource(CoreUiR.string.plate_calculator_preset_squat, formatted, unitSuffix)
        BarPresetKind.Ez ->
            stringResource(CoreUiR.string.plate_calculator_preset_ez, formatted, unitSuffix)
        BarPresetKind.Technique ->
            stringResource(CoreUiR.string.plate_calculator_preset_technique, formatted, unitSuffix)
        BarPresetKind.Trap ->
            stringResource(CoreUiR.string.plate_calculator_preset_trap, formatted, unitSuffix)
        BarPresetKind.None ->
            stringResource(CoreUiR.string.plate_calculator_preset_none, unitSuffix)
    }
}

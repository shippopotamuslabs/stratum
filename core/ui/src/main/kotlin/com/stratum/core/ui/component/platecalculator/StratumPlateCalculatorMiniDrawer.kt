// STR-142 allowlist: dense plate-chip insets (2.dp) pinned; section gaps adopted.
@file:Suppress("ForbiddenRawSpacingLiteralInUi")
package com.stratum.core.ui.component.platecalculator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.formatWeightAmount
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Mini drawer that slides out from the top of the custom set-log keyboard.
 * Displays a one-sided barbell sleeve breakdown, plate counts, and an expand chevron.
 */
@Composable
fun StratumPlateCalculatorMiniDrawer(
    plateLoad: PlateLoadUi,
    onExpandClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unitSuffix = plateLoad.unitLabel

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(StratumThemeAccessor.shapes.panel)
            .background(StratumThemeAccessor.colors.surfaceRaised)
            .border(
                width = StratumThemeAccessor.shapes.borderWidth,
                color = if (plateLoad.isValid) {
                    StratumThemeAccessor.colors.borderSubtle
                } else {
                    StratumThemeAccessor.colors.error.copy(alpha = 0.5f)
                },
                shape = StratumThemeAccessor.shapes.panel,
            )
            .padding(horizontal = StratumSpacing.s8, vertical = StratumSpacing.s8),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
        ) {
            StratumPlateBarbellDiagram(
                platesPerSide = if (plateLoad.isValid) plateLoad.platesPerSide else emptyList(),
                isMini = true,
                showPerSideBadge = false,
                modifier = Modifier.width(100.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
            ) {
                when (plateLoad.status) {
                    PlateLoadStatusUi.BelowBarWeight -> {
                        StratumText(
                            text = stringResource(CoreUiR.string.plate_calculator_below_bar),
                            style = StratumThemeAccessor.typography.bodyLarge.copy(
                                fontSize = 13.sp,
                                lineHeight = 16.sp,
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
                            style = StratumThemeAccessor.typography.sectionLabel.copy(
                                fontSize = 10.sp,
                                color = StratumThemeAccessor.colors.textMuted,
                            ),
                        )
                    }
                    PlateLoadStatusUi.CannotMatchWithAvailablePlates -> {
                        StratumText(
                            text = stringResource(CoreUiR.string.plate_calculator_cannot_match),
                            style = StratumThemeAccessor.typography.bodyLarge.copy(
                                fontSize = 13.sp,
                                lineHeight = 16.sp,
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
                            style = StratumThemeAccessor.typography.sectionLabel.copy(
                                fontSize = 10.sp,
                                color = StratumThemeAccessor.colors.textMuted,
                            ),
                        )
                    }
                    PlateLoadStatusUi.ExactMatch -> {
                        if (plateLoad.platesPerSide.isEmpty()) {
                            StratumText(
                                text = if (plateLoad.totalWeight > 0.0) {
                                    stringResource(
                                        CoreUiR.string.plate_calculator_bar_only,
                                        formatWeightAmount(plateLoad.barWeight),
                                        unitSuffix,
                                    )
                                } else {
                                    stringResource(
                                        CoreUiR.string.plate_calculator_empty_bar,
                                        unitSuffix,
                                    )
                                },
                                style = StratumThemeAccessor.typography.bodyLarge.copy(
                                    fontSize = 13.sp,
                                    lineHeight = 16.sp,
                                    color = StratumThemeAccessor.colors.textMuted,
                                ),
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                plateLoad.platesPerSide.forEach { plate ->
                                    PlateBadgePill(plate = plate)
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
                        ) {
                            StratumText(
                                text = stringResource(
                                    CoreUiR.string.plate_calculator_base,
                                    formatWeightAmount(plateLoad.barWeight).ifBlank { "0" },
                                    unitSuffix,
                                ),
                                style = StratumThemeAccessor.typography.sectionLabel.copy(
                                    fontSize = 10.sp,
                                    color = StratumThemeAccessor.colors.textMuted,
                                ),
                            )
                            if (plateLoad.totalWeight > 0.0) {
                                StratumText(
                                    text = stringResource(
                                        CoreUiR.string.plate_calculator_total_dot,
                                        formatWeightAmount(plateLoad.totalWeight),
                                        unitSuffix,
                                    ),
                                    style = StratumThemeAccessor.typography.sectionLabel.copy(
                                        fontSize = 10.sp,
                                        color = StratumThemeAccessor.colors.accent,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                StratumIconButton(
                    onClick = onExpandClick,
                    modifier = Modifier.size(36.dp),
                    containerColor = StratumThemeAccessor.colors.surfaceSunken,
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(CoreUiR.string.plate_calculator_expand),
                        tint = StratumThemeAccessor.colors.accent,
                        modifier = Modifier.size(22.dp),
                    )
                }

                StratumIconButton(
                    onClick = onCloseClick,
                    modifier = Modifier.size(36.dp),
                    containerColor = StratumThemeAccessor.colors.surfaceSunken,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(CoreUiR.string.plate_calculator_collapse),
                        tint = StratumThemeAccessor.colors.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlateBadgePill(
    plate: PlateItemUi,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(StratumThemeAccessor.shapes.chip)
            .background(StratumThemeAccessor.colors.surfaceSunken)
            .border(
                width = StratumThemeAccessor.shapes.borderWidth,
                color = StratumThemeAccessor.colors.borderSubtle,
                shape = StratumThemeAccessor.shapes.chip,
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(plate.color),
        )
        StratumNumber(
            text = "${plate.count} × ${plate.label}",
            style = StratumThemeAccessor.typography.bodyLarge.copy(
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = StratumThemeAccessor.colors.textPrimary,
            ),
        )
    }
}

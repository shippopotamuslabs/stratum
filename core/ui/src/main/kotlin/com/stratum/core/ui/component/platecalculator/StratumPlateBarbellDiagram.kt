package com.stratum.core.ui.component.platecalculator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Visual Compose diagram of a barbell sleeve loaded with weight plates (one side / per side).
 * Accurately proportions the thinner shaft, compact collar ring, thicker sleeve, and loaded plates.
 */
@Composable
fun StratumPlateBarbellDiagram(
    platesPerSide: List<PlateItemUi>,
    modifier: Modifier = Modifier,
    isMini: Boolean = false,
    showPerSideBadge: Boolean = true,
) {
    val totalHeight = if (isMini) 56.dp else 104.dp
    val barShaftHeight = if (isMini) 6.dp else 10.dp
    val barShaftWidth = if (isMini) 14.dp else 60.dp
    val collarHeight = if (isMini) 16.dp else 28.dp
    val collarWidth = if (isMini) 5.dp else 10.dp
    val sleeveHeight = if (isMini) 11.dp else 18.dp
    val sleeveWidth = if (isMini) 68.dp else 135.dp

    // Expand list of PlateItemUi (e.g. 45 lb x 2) into individual plate items in order.
    val individualPlates = platesPerSide.flatMap { plate ->
        List(plate.count) { plate }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight)
            .clip(StratumThemeAccessor.shapes.panel)
            .background(StratumThemeAccessor.colors.surfaceSunken)
            .border(
                width = StratumThemeAccessor.shapes.borderWidth,
                color = StratumThemeAccessor.colors.borderSubtle,
                shape = StratumThemeAccessor.shapes.panel,
            )
            .padding(horizontal = if (isMini) 6.dp else 16.dp),
        contentAlignment = if (isMini) Alignment.CenterStart else Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            // 1. Barbell Shaft (Inner center bar - thinner 28mm proportion)
            Box(
                modifier = Modifier
                    .width(barShaftWidth)
                    .height(barShaftHeight)
                    .clip(RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp))
                    .background(StratumThemeAccessor.colors.borderStrong),
            )

            // 2. Barbell Inner Collar Ring / Flange (Compact ring smaller than a 2.5 lb plate)
            Box(
                modifier = Modifier
                    .width(collarWidth)
                    .height(collarHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(StratumThemeAccessor.colors.textMuted)
                    .border(1.dp, StratumThemeAccessor.colors.borderStrong, RoundedCornerShape(2.dp)),
            )

            // 3. Sleeve area (Thicker 50mm proportion) with loaded plates
            Box(
                modifier = Modifier
                    .width(sleeveWidth)
                    .height(totalHeight),
                contentAlignment = Alignment.CenterStart,
            ) {
                // Background barbell sleeve shaft (visibly thicker than the center bar)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sleeveHeight)
                        .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                        .background(StratumThemeAccessor.colors.borderSubtle)
                        .border(
                            width = 1.dp,
                            color = StratumThemeAccessor.colors.borderStrong.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp),
                        ),
                )

                // Loaded Plates on the sleeve
                if (individualPlates.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        individualPlates.forEach { plateItem ->
                            PlateItem(
                                plate = plateItem,
                                maxHeight = totalHeight - (if (isMini) 10.dp else 16.dp),
                                isMini = isMini,
                            )
                        }
                    }
                }
            }
        }

        if (showPerSideBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = if (isMini) 4.dp else 8.dp, end = if (isMini) 4.dp else 8.dp)
                    .clip(StratumThemeAccessor.shapes.chip)
                    .background(StratumThemeAccessor.colors.surfaceRaised)
                    .border(
                        width = StratumThemeAccessor.shapes.borderWidth,
                        color = StratumThemeAccessor.colors.borderSubtle,
                        shape = StratumThemeAccessor.shapes.chip,
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                StratumText(
                    text = stringResource(
                        if (isMini) {
                            CoreUiR.string.plate_calculator_one_side_badge
                        } else {
                            CoreUiR.string.plate_calculator_per_side_badge
                        },
                    ),
                    style = StratumThemeAccessor.typography.sectionLabel.copy(
                        fontSize = if (isMini) 9.sp else 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = StratumThemeAccessor.colors.accent,
                )
            }
        }
    }
}

@Composable
private fun PlateItem(
    plate: PlateItemUi,
    maxHeight: androidx.compose.ui.unit.Dp,
    isMini: Boolean,
) {
    val width = if (isMini) (plate.widthDp * 0.7f) else plate.widthDp
    val itemHeight = maxHeight * plate.heightFraction
    val shape = RoundedCornerShape(2.dp)

    Box(
        modifier = Modifier
            .width(width)
            .height(itemHeight)
            .clip(shape)
            .background(plate.color)
            .border(1.dp, Color.Black.copy(alpha = 0.25f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (!isMini && width >= 12.dp && itemHeight >= 36.dp) {
            StratumNumber(
                text = plate.label,
                style = StratumThemeAccessor.typography.bodyLarge.copy(
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = plate.onColor,
                ),
                textAlign = TextAlign.Center,
            )
        }
    }
}

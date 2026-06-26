package com.stratum.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.domain.importing.LandmarkConfidenceTier
import com.stratum.domain.importing.UserEffectivenessTier

/**
 * Three ascending bars — [filledBars] of 1 (low) through 3 (high) are filled.
 */
@Composable
fun StratumSignalBars(
    filledBars: Int,
    modifier: Modifier = Modifier,
    contentDescription: String,
) {
    val filledCount = filledBars.coerceIn(SIGNAL_BAR_MIN, SIGNAL_BAR_MAX)
    val filledColor = StratumThemeAccessor.colors.accent
    val unfilledColor = StratumThemeAccessor.colors.borderSubtle

    Canvas(
        modifier =
            modifier
                .size(SIGNAL_BARS_WIDTH_DP.dp, SIGNAL_BARS_HEIGHT_DP.dp)
                .semantics { this.contentDescription = contentDescription },
    ) {
        val barWidth = SIGNAL_BAR_WIDTH_DP.dp.toPx()
        val gap = SIGNAL_BAR_GAP_DP.dp.toPx()
        val heights =
            listOf(
                SIGNAL_BAR_HEIGHT_SHORT_DP.dp.toPx(),
                SIGNAL_BAR_HEIGHT_MID_DP.dp.toPx(),
                SIGNAL_BAR_HEIGHT_TALL_DP.dp.toPx(),
            )
        val canvasHeight = size.height

        heights.forEachIndexed { index, barHeight ->
            val x = index * (barWidth + gap)
            val y = canvasHeight - barHeight
            drawRoundRect(
                color = if (index < filledCount) filledColor else unfilledColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(SIGNAL_BAR_CORNER_DP.dp.toPx()),
            )
        }
    }
}

/** Three ascending bars indicating evidence confidence — 1 filled (low), 2 (medium), 3 (high). */
@Composable
fun StratumConfidenceBars(
    tier: LandmarkConfidenceTier,
    modifier: Modifier = Modifier,
    contentDescription: String,
) {
    StratumSignalBars(
        filledBars =
            when (tier) {
                LandmarkConfidenceTier.Low -> SIGNAL_BAR_LOW
                LandmarkConfidenceTier.Medium -> SIGNAL_BAR_MEDIUM
                LandmarkConfidenceTier.High -> SIGNAL_BAR_HIGH
            },
        modifier = modifier,
        contentDescription = contentDescription,
    )
}

/** Three ascending bars indicating relative lift efficacy — 1 filled (low), 2 (medium), 3 (high). */
@Composable
fun StratumEffectivenessBars(
    tier: UserEffectivenessTier,
    modifier: Modifier = Modifier,
    contentDescription: String,
) {
    StratumSignalBars(
        filledBars =
            when (tier) {
                UserEffectivenessTier.SomewhatEffective -> SIGNAL_BAR_LOW
                UserEffectivenessTier.ModeratelyEffective -> SIGNAL_BAR_MEDIUM
                UserEffectivenessTier.VeryEffective -> SIGNAL_BAR_HIGH
            },
        modifier = modifier,
        contentDescription = contentDescription,
    )
}

private const val SIGNAL_BAR_MIN = 1
private const val SIGNAL_BAR_MAX = 3
private const val SIGNAL_BAR_LOW = 1
private const val SIGNAL_BAR_MEDIUM = 2
private const val SIGNAL_BAR_HIGH = 3
private const val SIGNAL_BAR_WIDTH_DP = 4
private const val SIGNAL_BAR_GAP_DP = 3
private const val SIGNAL_BAR_HEIGHT_SHORT_DP = 6
private const val SIGNAL_BAR_HEIGHT_MID_DP = 10
private const val SIGNAL_BAR_HEIGHT_TALL_DP = 14
private const val SIGNAL_BAR_CORNER_DP = 1
private const val SIGNAL_BARS_WIDTH_DP = SIGNAL_BAR_WIDTH_DP * 3 + SIGNAL_BAR_GAP_DP * 2
private const val SIGNAL_BARS_HEIGHT_DP = SIGNAL_BAR_HEIGHT_TALL_DP

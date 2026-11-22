package com.stratum.feature.workout.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.workout.R

private val REST_RING_SIZE: Dp = 224.dp
private val REST_RING_STROKE: Dp = 12.dp
private const val REST_RING_START_DEG = -90f
private const val FULL_CIRCLE_DEG = 360f

/**
 * Fraction of the rest period still remaining, clamped to `0f..1f`. A zero or
 * negative total (no rest configured) reads as empty rather than dividing by zero;
 * a +10s adjustment past the original total reads as a full ring.
 */
internal fun restRemainingFraction(
    remainingSeconds: Int,
    totalSeconds: Int,
): Float =
    if (totalSeconds <= 0) {
        0f
    } else {
        (remainingSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f)
    }

/**
 * STR-329: the rest countdown as a circular progress ring that depletes as the
 * rest elapses, with the remaining time (M:SS) and a REMAINING caption centered
 * inside. The arc starts at 12 o'clock and sweeps clockwise.
 */
@Composable
internal fun RestCountdownRing(
    remainingSeconds: Int,
    totalSeconds: Int,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val typography = StratumThemeAccessor.typography
    val fraction = restRemainingFraction(remainingSeconds, totalSeconds)
    Box(
        modifier = modifier.size(REST_RING_SIZE),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = REST_RING_STROKE.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = colors.borderSubtle,
                startAngle = 0f,
                sweepAngle = FULL_CIRCLE_DEG,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            if (fraction > 0f) {
                drawArc(
                    color = colors.accent,
                    startAngle = REST_RING_START_DEG,
                    sweepAngle = FULL_CIRCLE_DEG * fraction,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
        ) {
            StratumText(
                text = formatRestDisplay(remainingSeconds),
                style = typography.displayLarge,
            )
            StratumText(
                text = stringResource(R.string.workout_coach_rest_remaining).uppercase(),
                muted = true,
                style = typography.sectionLabel,
            )
        }
    }
}

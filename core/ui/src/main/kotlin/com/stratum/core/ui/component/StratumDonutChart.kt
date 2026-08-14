package com.stratum.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.min

/**
 * Compact donut chart. Slices are sized by [DonutSliceUi.fraction]. [centerContent] sits in the
 * hole. Slice names and values belong in a legend beside or below the ring — a compact dashboard
 * donut cannot fit duration strings on the stroke without clipping.
 */
@Composable
fun StratumDonutChart(
    slices: List<DonutSliceUi>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    holeRatio: Float = DONUT_HOLE_RATIO,
    centerContent: @Composable BoxScope.() -> Unit = {},
) {
    val layouts = remember(slices) { layoutDonutSlices(slices.map { it.fraction }) }

    Box(
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = min(size.width, size.height)
            val stroke = diameter * (1f - holeRatio) / 2f
            val inset = stroke / 2f
            val topLeft = Offset(
                (size.width - diameter) / 2f + inset,
                (size.height - diameter) / 2f + inset,
            )
            val arcSize = Size(diameter - stroke, diameter - stroke)

            slices.zip(layouts).forEach { (slice, layout) ->
                if (layout.sweepAngleDeg <= 0f) return@forEach
                drawArc(
                    color = slice.color,
                    startAngle = layout.startAngleDeg,
                    sweepAngle = layout.sweepAngleDeg,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
            }
        }
        centerContent()
    }
}

@Immutable
data class DonutSliceUi(
    val fraction: Float,
    val color: Color,
)

@Immutable
internal data class DonutSliceLayout(
    val startAngleDeg: Float,
    val sweepAngleDeg: Float,
)

/** Lays out donut sweeps starting at 12 o'clock. */
internal fun layoutDonutSlices(
    fractions: List<Float>,
    startAngleDeg: Float = DONUT_START_ANGLE_DEG,
): List<DonutSliceLayout> {
    var cursor = startAngleDeg
    return fractions.map { raw ->
        val sweep = (raw.coerceAtLeast(0f) * 360f)
        val layout = DonutSliceLayout(
            startAngleDeg = cursor,
            sweepAngleDeg = sweep,
        )
        cursor += sweep
        layout
    }
}

private const val DONUT_HOLE_RATIO = 0.52f
private const val DONUT_START_ANGLE_DEG = -90f

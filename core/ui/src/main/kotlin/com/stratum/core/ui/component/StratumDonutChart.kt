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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.stratum.core.ui.theme.StratumThemeAccessor
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Compact donut chart. Slices are sized by [DonutSliceUi.fraction]; optional [DonutSliceUi.label]
 * is drawn on the ring when the sweep is large enough. [centerContent] sits in the hole.
 */
@Composable
fun StratumDonutChart(
    slices: List<DonutSliceUi>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    holeRatio: Float = DONUT_HOLE_RATIO,
    centerContent: @Composable BoxScope.() -> Unit = {},
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = SLICE_LABEL_SP.sp,
        color = StratumThemeAccessor.colors.textPrimary,
    )
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
            val ringRadius = (diameter - stroke) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

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
                val label = slice.label
                if (layout.showLabel && label.isNotEmpty()) {
                    val radians = Math.toRadians(layout.labelAngleDeg.toDouble())
                    val labelCenter = Offset(
                        center.x + ringRadius * cos(radians).toFloat(),
                        center.y + ringRadius * sin(radians).toFloat(),
                    )
                    val textLayout = measurer.measure(text = label, style = labelStyle.copy(color = slice.labelColor()))
                    val arcLength = (layout.sweepAngleDeg / 360f) * (2f * Math.PI.toFloat() * ringRadius)
                    if (textLayout.size.width < arcLength * LABEL_ARC_FIT) {
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(
                                labelCenter.x - textLayout.size.width / 2f,
                                labelCenter.y - textLayout.size.height / 2f,
                            ),
                        )
                    }
                }
            }
        }
        centerContent()
    }
}

@Immutable
data class DonutSliceUi(
    val fraction: Float,
    val color: Color,
    val label: String = "",
)

@Immutable
internal data class DonutSliceLayout(
    val startAngleDeg: Float,
    val sweepAngleDeg: Float,
    val labelAngleDeg: Float,
    val showLabel: Boolean,
)

/**
 * Lays out donut sweeps starting at 12 o'clock. Labels are offered when a slice is wide enough
 * that a short duration string can sit on the ring without colliding with neighbors.
 */
internal fun layoutDonutSlices(
    fractions: List<Float>,
    startAngleDeg: Float = DONUT_START_ANGLE_DEG,
    minLabelSweepDeg: Float = MIN_LABEL_SWEEP_DEG,
): List<DonutSliceLayout> {
    var cursor = startAngleDeg
    return fractions.map { raw ->
        val sweep = (raw.coerceAtLeast(0f) * 360f)
        val layout = DonutSliceLayout(
            startAngleDeg = cursor,
            sweepAngleDeg = sweep,
            labelAngleDeg = cursor + sweep / 2f,
            showLabel = sweep >= minLabelSweepDeg,
        )
        cursor += sweep
        layout
    }
}

private fun DonutSliceUi.labelColor(): Color {
    val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    return if (luminance > LABEL_LUMINANCE_CUTOFF) LABEL_ON_LIGHT else LABEL_ON_DARK
}

private const val DONUT_HOLE_RATIO = 0.58f
private const val DONUT_START_ANGLE_DEG = -90f
private const val MIN_LABEL_SWEEP_DEG = 24f
private const val SLICE_LABEL_SP = 8
private const val LABEL_ARC_FIT = 0.85f
private const val LABEL_LUMINANCE_CUTOFF = 0.55f
private val LABEL_ON_LIGHT = Color(0xFF111827)
private val LABEL_ON_DARK = Color(0xFFFFFFFF)

package com.stratum.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * A compact dual-line trend chart for the dashboard. Designed for half-width grid cells: no axes,
 * ticks, or labels — the card header carries the headline numbers, the chart carries the shape.
 *
 * Rendered by the Vico chart library as two [LineCartesianLayer] series in one axis-less
 * [com.patrykandpatrick.vico.compose.cartesian.CartesianChart]. Vico owns the joint min/max
 * normalization so the two lines share a common visual scale (replacing the hand-rolled scaling
 * this component used to do). The [primarySeries] (progress/strength) uses [primaryColor]; the
 * optional [secondarySeries] (volume) uses [secondaryColor]. Both lines have no point markers —
 * only the shape is drawn.
 *
 * @param primarySeries the main line (e.g. composite progress index, or peak e1RM for a lift).
 * @param secondarySeries an optional secondary line (e.g. weekly volume) drawn alongside.
 * @param primaryColor semantic color for the primary line (e.g. `StratumColors.setComplete`).
 * @param secondaryColor semantic color for the secondary line (e.g. `StratumColors.textMuted`).
 * @param contentDescription accessibility description of what the chart shows.
 * @param onPointTap reserved for the Phase 2 tap-to-inspect feature; no-op by default.
 */
@Composable
@Suppress("UnusedParameter") // onPointTap reserved for Phase 2 tap-to-inspect
fun StratumTrendChart(
    primarySeries: List<TrendPointUi>,
    modifier: Modifier = Modifier,
    secondarySeries: List<TrendPointUi>? = null,
    primaryColor: Color = StratumThemeAccessor.colors.setComplete,
    secondaryColor: Color = StratumThemeAccessor.colors.textMuted,
    contentDescription: String,
    onPointTap: ((Int) -> Unit)? = null,
) {
    val hasSecondary = secondarySeries != null && secondarySeries.size >= MIN_POINTS_FOR_LINE
    val hasPrimary = primarySeries.size >= MIN_POINTS_FOR_LINE

    if (!hasPrimary && !hasSecondary) {
        // No drawable series: render the faint baseline only, matching the pre-Vico empty state.
        BaselineOnly(modifier.semantics { this.contentDescription = contentDescription })
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    // Materialize the secondary values up front as a non-null list so the `lineModel` block below
    // doesn't need a smart-cast that Kotlin can't carry across the nested lambdas.
    val secondaryValues: List<Float> = secondarySeries?.map { it.value } ?: emptyList()
    LaunchedEffect(primarySeries, secondaryValues) {
        modelProducer.runTransaction {
            lineModel {
                // Each non-empty series contributes a line; Vico normalizes them jointly.
                if (hasPrimary) series(y = primarySeries.map { it.value })
                if (hasSecondary) series(y = secondaryValues)
            }
        }
    }

    val chart =
        rememberCartesianChart(
            // A single line layer holds both series; lineProvider returns one Line per series
            // index. Both lines use the same stroke weight and rounded caps; only the color
            // differs.
            rememberLineCartesianLayer(
                lineProvider =
                    LineCartesianLayer.LineProvider.series(
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(Fill(primaryColor)),
                            stroke =
                                LineCartesianLayer.LineStroke.Continuous(
                                    thickness = LINE_STROKE_WIDTH_DP.dp,
                                    cap = StrokeCap.Round,
                                ),
                            // pointProvider defaults to null -> no per-point dots (sparkline).
                        ),
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(Fill(secondaryColor)),
                            stroke =
                                LineCartesianLayer.LineStroke.Continuous(
                                    thickness = LINE_STROKE_WIDTH_DP.dp,
                                    cap = StrokeCap.Round,
                                ),
                        ),
                    ),
            ),
            // All axes, marker, legend, fadingEdges, and decorations default to null/empty ->
            // an axis-less, grid-less, marker-less sparkline.
        )

    CartesianChartHost(
        chart = chart,
        modelProducer = modelProducer,
        modifier =
            modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT_DP.dp)
                .semantics { this.contentDescription = contentDescription },
    )
}

/** Draws only the faint baseline — used when there is too little data to plot a line. */
@Composable
private fun BaselineOnly(modifier: Modifier) {
    val baselineColor = StratumThemeAccessor.colors.borderSubtle
    Canvas(modifier.fillMaxWidth().height(CHART_HEIGHT_DP.dp)) {
        drawLine(
            color = baselineColor,
            start = Offset(STROKE_PADDING_PX, size.height - STROKE_PADDING_PX),
            end = Offset(size.width - STROKE_PADDING_PX, size.height - STROKE_PADDING_PX),
            strokeWidth = BASELINE_STROKE_WIDTH_DP.dp.toPx(),
        )
    }
}

/**
 * Presentation-only point for [StratumTrendChart]. [value] is the raw series value (sets, e1RM, or
 * composite index) — the chart normalizes jointly so two series share a visual scale. [weekIndex] is
 * a 0-based positional index reserved for the Phase 2 tap-to-inspect feature.
 */
data class TrendPointUi(
    val weekIndex: Int,
    val value: Float,
)

private const val CHART_HEIGHT_DP = 48
private const val LINE_STROKE_WIDTH_DP = 2
private const val BASELINE_STROKE_WIDTH_DP = 1
private const val STROKE_PADDING_PX = 4f
private const val MIN_POINTS_FOR_LINE = 2

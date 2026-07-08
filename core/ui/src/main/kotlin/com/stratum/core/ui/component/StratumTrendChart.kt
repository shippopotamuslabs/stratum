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
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A compact dual-line trend chart for the dashboard. Designed for half-width grid cells: no axes,
 * ticks, or labels — the card header carries the headline numbers, the chart carries the shape.
 *
 * Rendered by the Vico chart library as two [LineCartesianLayer] series in one axis-less
 * [com.patrykandpatrick.vico.compose.cartesian.CartesianChart]. Each series is independently
 * normalized into a shared centered band via [normalizeSparklineSeries] before being handed to
 * Vico — the two lines routinely measure different units at very different magnitudes (e.g.
 * weekly set count vs. a 100+ progress index or a 200+ e1RM), so a single shared Y scale would
 * pin one line flat and park the other at the top. Independent z-score normalization lets each
 * line show its own shape while both stay vertically centered. The [primarySeries]
 * (progress/strength) uses [primaryColor]; the optional [secondarySeries] (volume) uses
 * [secondaryColor]. Both lines have no point markers — only the shape is drawn.
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
                // Each series is normalized independently into the same centered band, so the two
                // lines share a visual vertical position even when their raw units differ by orders
                // of magnitude (set count vs. a progress index / e1RM). Vico then sees two series
                // already occupying the same band and its joint scaling becomes a no-op.
                if (hasPrimary) series(y = normalizeSparklineSeries(primarySeries.map { it.value }))
                if (hasSecondary) series(y = normalizeSparklineSeries(secondaryValues))
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
 * composite index) — the chart normalizes each series independently into a centered band so two
 * differently-scaled series each show their shape. [weekIndex] is a 0-based positional index
 * reserved for the Phase 2 tap-to-inspect feature.
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

/**
 * Band the normalized series occupies. [BAND_MIN] / [BAND_MAX] are symmetric around the midline so
 * every line stays vertically centered; the line never escapes this range (z-scores are clamped).
 */
private const val BAND_MIN = 0.15f
private const val BAND_MAX = 0.85f
private const val BAND_MID = (BAND_MIN + BAND_MAX) / 2f
private const val BAND_HALF = (BAND_MAX - BAND_MIN) / 2f

/**
 * Gain cap in standard deviations. A z-score of ±[GAIN] (≈1.67σ) spans the full band half-width;
 * anything beyond is clamped, so an extreme outlier cannot flatten the rest of the line.
 */
private const val GAIN = 0.6f

/**
 * Floor on the standard deviation as a fraction of |mean|. Without it, a near-constant series
 * (tiny std) would blow tiny fluctuations up to the band edges; flooring keeps steady data
 * honestly flat and centered.
 */
private const val STD_FLOOR_RATIO = 0.04f

private const val NORMALIZE_EPSILON = 1e-6f

/**
 * Normalizes a sparkline series into a centered band ([BAND_MIN]..[BAND_MAX]) independently of any
 * other series. Conservative: a std floor ([STD_FLOOR_RATIO]) and a gain cap ([GAIN]) keep
 * near-constant data flat/centered while genuinely varying data fills the band. Series shorter than
 * [MIN_POINTS_FOR_LINE] are returned as-is (caller skips drawing them). Pure and testable.
 */
internal fun normalizeSparklineSeries(values: List<Float>): List<Float> {
    if (values.size < MIN_POINTS_FOR_LINE) return values
    val mean = values.average().toFloat()
    val variance = values.fold(0f) { acc, v -> acc + (v - mean) * (v - mean) } / values.size
    val rawStd = sqrt(variance)
    // The floor keeps near-constant series (tiny raw std) honest: a larger std shrinks z-scores
    // toward zero so small fluctuations don't get magnified to the band edges.
    val std = maxOf(rawStd, abs(mean) * STD_FLOOR_RATIO, NORMALIZE_EPSILON)
    return values.map { v ->
        val z = (v - mean) / std
        // Clamp to ±GAIN σ, then linearly map [-GAIN, GAIN] onto [BAND_MIN, BAND_MAX].
        BAND_MID + (z.coerceIn(-GAIN, GAIN) / GAIN) * BAND_HALF
    }
}

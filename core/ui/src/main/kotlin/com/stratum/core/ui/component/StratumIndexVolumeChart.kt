package com.stratum.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Compact muscle dashboard chart: **index bars about zero** (progress vs early baseline) plus a
 * muted **volume line** (weekly hard sets). Designed for half-width grid cells — no axes or labels.
 *
 * Dual Y scales via [Axis.Position.Vertical]: columns on Start (index, zero included), volume line
 * on End. That keeps tiny log-index values readable without crushing set counts, and avoids the
 * independent z-score dual-sparkline that erased absolute direction.
 *
 * @param indexSeries weekly outcome-index values (already baseline-relative; 0 = at baseline).
 * @param volumeSeries weekly hard-set counts, aligned on the same [TrendPointUi.weekIndex] space.
 * @param indexPositiveColor bars at/above baseline (darker green — `setComplete`).
 * @param indexNegativeColor bars below baseline (lighter green — not muted grey).
 * @param volumeColor volume line color.
 * @param contentDescription accessibility description.
 */
@Composable
fun StratumIndexVolumeChart(
    indexSeries: List<TrendPointUi>,
    volumeSeries: List<TrendPointUi>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    indexPositiveColor: Color = Color.Unspecified,
    indexNegativeColor: Color = Color.Unspecified,
    volumeColor: Color = Color.Unspecified,
    zeroLineColor: Color = Color.Unspecified,
) {
    val colors = StratumThemeAccessor.colors
    val resolvedPositive =
        if (indexPositiveColor == Color.Unspecified) colors.setComplete else indexPositiveColor
    // Lighter green for below-baseline: blend setComplete toward textPrimary (mint on dark, not grey).
    val resolvedNegative =
        if (indexNegativeColor == Color.Unspecified) {
            lerp(colors.setComplete, colors.textPrimary, INDEX_NEGATIVE_LIGHTEN_FRACTION)
        } else {
            indexNegativeColor
        }
    val resolvedVolume =
        if (volumeColor == Color.Unspecified) {
            colors.textMuted.copy(alpha = VOLUME_LINE_ALPHA)
        } else {
            volumeColor
        }
    val resolvedZero =
        if (zeroLineColor == Color.Unspecified) colors.borderSubtle else zeroLineColor

    val hasIndex = indexSeries.isNotEmpty()
    val hasVolume = volumeSeries.size >= MIN_POINTS_FOR_LINE

    if (!hasIndex && !hasVolume) {
        IndexVolumeBaselineOnly(
            modifier = modifier.semantics { this.contentDescription = contentDescription },
            color = resolvedZero,
        )
        return
    }

    val positiveColumn = rememberLineComponent(
        fill = Fill(resolvedPositive),
        thickness = COLUMN_WIDTH_DP.dp,
    )
    val negativeColumn = rememberLineComponent(
        fill = Fill(resolvedNegative),
        thickness = COLUMN_WIDTH_DP.dp,
    )
    val zeroLine = rememberLineComponent(
        fill = Fill(resolvedZero),
        thickness = ZERO_LINE_THICKNESS_DP.dp,
    )
    val columnProvider = remember(positiveColumn, negativeColumn) {
        SignedColumnProvider(positive = positiveColumn, negative = negativeColumn)
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(indexSeries, volumeSeries) {
        modelProducer.runTransaction {
            if (hasIndex) {
                columnModel {
                    series(
                        x = indexSeries.map { it.weekIndex },
                        y = indexSeries.map { it.value },
                    )
                }
            }
            if (hasVolume) {
                lineModel {
                    series(
                        x = volumeSeries.map { it.weekIndex },
                        y = volumeSeries.map { it.value },
                    )
                }
            }
        }
    }

    val decorations = remember(zeroLine, hasIndex) {
        if (hasIndex) {
            listOf(
                HorizontalLine(
                    y = { 0.0 },
                    line = zeroLine,
                    labelComponent = null,
                    label = { "" },
                    verticalAxisPosition = Axis.Position.Vertical.Start,
                ),
            )
        } else {
            emptyList()
        }
    }

    val chart = rememberCartesianChart(
        rememberColumnCartesianLayer(
            columnProvider = columnProvider,
            columnCollectionSpacing = COLUMN_SPACING_DP.dp,
            verticalAxisPosition = Axis.Position.Vertical.Start,
        ),
        rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(
                LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(Fill(resolvedVolume)),
                    stroke = LineCartesianLayer.LineStroke.Continuous(
                        thickness = VOLUME_STROKE_DP.dp,
                        cap = StrokeCap.Round,
                    ),
                ),
            ),
            verticalAxisPosition = Axis.Position.Vertical.End,
        ),
        decorations = decorations,
    )

    CartesianChartHost(
        chart = chart,
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT_DP.dp)
            .semantics { this.contentDescription = contentDescription },
    )
}

/** Positive index bars use [positive]; below-baseline bars use [negative]. */
@Immutable
private class SignedColumnProvider(
    private val positive: LineComponent,
    private val negative: LineComponent,
) : ColumnCartesianLayer.ColumnProvider {
    override fun getColumn(
        entry: ColumnCartesianLayerModel.Entry,
        extraStore: ExtraStore,
    ): LineComponent = if (entry.y >= 0.0) positive else negative

    override fun getWidestSeriesColumn(
        seriesKey: Any,
        seriesIndex: Int,
        extraStore: ExtraStore,
    ): LineComponent = positive
}

@Composable
private fun IndexVolumeBaselineOnly(modifier: Modifier, color: Color) {
    Canvas(modifier.fillMaxWidth().height(CHART_HEIGHT_DP.dp)) {
        val y = size.height / 2f
        drawLine(
            color = color,
            start = Offset(STROKE_PADDING_PX, y),
            end = Offset(size.width - STROKE_PADDING_PX, y),
            strokeWidth = ZERO_LINE_THICKNESS_DP.dp.toPx(),
        )
    }
}

private const val CHART_HEIGHT_DP = 48
private const val COLUMN_WIDTH_DP = 3
private const val COLUMN_SPACING_DP = 1
private const val VOLUME_STROKE_DP = 2
private const val ZERO_LINE_THICKNESS_DP = 1
private const val STROKE_PADDING_PX = 4f
private const val MIN_POINTS_FOR_LINE = 2
private const val VOLUME_LINE_ALPHA = 0.55f
/** How far to lighten [setComplete] toward [textPrimary] for below-baseline bars. */
private const val INDEX_NEGATIVE_LIGHTEN_FRACTION = 0.55f

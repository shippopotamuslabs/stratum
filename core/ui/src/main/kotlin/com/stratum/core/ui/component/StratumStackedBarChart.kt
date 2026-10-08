package com.stratum.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisTickComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * One stack segment of [StratumStackedBarChart]: its point series plus its fill color.
 * Keeping points and color together makes the series/color alignment structural
 * instead of a runtime-checked parallel-list contract.
 */
data class StackedSegment(
    val points: List<TrendPointUi>,
    val color: Color,
)

/**
 * Stacked column chart: each x position renders its segments stacked into one bar
 * (e.g. sleep stages per night on the dashboard recovery page). Tapping a bar shows a
 * color-coded marker with each segment's value.
 *
 * Horizontal pan is disabled so a parent pager can fling.
 *
 * @param segments one entry per stack segment, index-aligned by [TrendPointUi.weekIndex]
 * (e.g. light/deep/REM/awake nightly hours).
 * @param contentDescription accessibility description.
 * @param dayLabels short weekday labels per x position; empty omits the bottom axis.
 * @param fillHeight when true, skip the compact intrinsic height and fill incoming constraints.
 * @param yScale when non-null, draw a quiet start axis labeled via [formatChartNumber] and range
 * the bars via [rangeProviderFor]. Null (default) keeps Vico's auto range with no axis.
 */
@Composable
fun StratumStackedBarChart(
    segments: List<StackedSegment>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    dayLabels: List<String> = emptyList(),
    fillHeight: Boolean = false,
    yScale: DetailYScale? = null,
) {
    val colors = StratumThemeAccessor.colors
    val hasData = segments.any { segment -> segment.points.any { it.value > 0f } }

    val segmentColumns = segments.map { segment ->
        rememberLineComponent(
            fill = Fill(segment.color),
            thickness = STACKED_COLUMN_WIDTH_DP.dp,
        )
    }
    val columnProvider = remember(segmentColumns) {
        ColumnCartesianLayer.ColumnProvider.series(segmentColumns)
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    val reporter = LocalUiRenderErrorReporter.current
    var modelFailed by remember(segments) { mutableStateOf(false) }
    if (modelFailed || !hasData) {
        StackedBarBaselineOnly(
            modifier = modifier.semantics { this.contentDescription = contentDescription },
            color = colors.borderSubtle,
        )
        return
    }
    LaunchedEffect(segments) {
        runCatchingChartModel(reporter, onFailure = { modelFailed = true }) {
            modelProducer.runTransaction {
                columnModel {
                    segments.forEach { segment ->
                        series(
                            x = segment.points.map { it.weekIndex },
                            y = segment.points.map { it.value },
                        )
                    }
                }
            }
        }
    }
    val inspectionModel =
        rememberInspectionChartModel(key = segments) {
            CartesianChartModel(
                models =
                    listOf(
                        syncStackedColumnLayerModel(series = segments.map { it.points }),
                    ),
            )
        }

    val labelStyle = TextStyle(
        color = colors.textMuted,
        fontSize = STACKED_AXIS_LABEL_SP.sp,
    )
    val axisLabel = rememberAxisLabelComponent(style = labelStyle)
    val invisibleLine = rememberAxisLineComponent(
        fill = Fill(Color.Transparent),
        thickness = 0.dp,
    )
    val invisibleTick = rememberAxisTickComponent(
        fill = Fill(Color.Transparent),
        thickness = 0.dp,
    )
    val labelsForAxis = dayLabels
    val xFormatter = remember(labelsForAxis) {
        CartesianValueFormatter { _, value, _ ->
            labelsForAxis.getOrNull(value.toInt()).orEmpty()
        }
    }
    val bottomAxis = if (labelsForAxis.isNotEmpty()) {
        HorizontalAxis.rememberBottom(
            line = invisibleLine,
            label = axisLabel,
            tick = invisibleTick,
            guideline = null,
            valueFormatter = xFormatter,
        )
    } else {
        null
    }
    val startAxis = if (yScale != null) {
        rememberQuietStartAxis(
            label = axisLabel,
            line = invisibleLine,
            tick = invisibleTick,
            valueFormatter = remember(yScale) {
                CartesianValueFormatter { _, value, _ -> formatChartNumber(value, yScale) }
            },
            labelCount = yTickCountFor(yScale),
        )
    } else {
        null
    }
    val columnRangeProvider = remember(yScale) {
        yScale?.let(::rangeProviderFor) ?: CartesianLayerRangeProvider.auto()
    }

    val marker = rememberStratumChartMarker(
        decimalCount = 1,
        colorCode = true,
        valueFormat = CompactChartValueFormat.HoursMinutes,
    )
    val chart = rememberCartesianChart(
        rememberColumnCartesianLayer(
            columnProvider = columnProvider,
            columnCollectionSpacing = STACKED_COLUMN_SPACING_DP.dp,
            rangeProvider = columnRangeProvider,
            mergeMode = { ColumnCartesianLayer.MergeMode.Stacked },
        ),
        bottomAxis = bottomAxis,
        startAxis = startAxis,
        marker = marker,
        markerController = rememberStratumChartMarkerController(),
    )

    val sizedModifier = if (fillHeight) {
        modifier.fillMaxWidth()
    } else {
        modifier
            .fillMaxWidth()
            .height(
                if (labelsForAxis.isNotEmpty()) {
                    STACKED_CHART_HEIGHT_WITH_LABELS_DP.dp
                } else {
                    STACKED_CHART_HEIGHT_DP.dp
                },
            )
    }
    StratumCartesianChartHost(
        chart = chart,
        modelProducer = modelProducer,
        inspectionModel = inspectionModel,
        modifier = sizedModifier,
        contentDescription = contentDescription,
    )
}

@Composable
private fun StackedBarBaselineOnly(modifier: Modifier, color: Color) {
    Canvas(modifier.fillMaxWidth().height(STACKED_CHART_HEIGHT_DP.dp)) {
        val y = size.height / 2f
        drawLine(
            color = color,
            start = Offset(STACKED_STROKE_PADDING_PX, y),
            end = Offset(size.width - STACKED_STROKE_PADDING_PX, y),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

private const val STACKED_CHART_HEIGHT_DP = 120
private const val STACKED_CHART_HEIGHT_WITH_LABELS_DP = 138
private const val STACKED_COLUMN_WIDTH_DP = 14
private const val STACKED_COLUMN_SPACING_DP = 8
private const val STACKED_AXIS_LABEL_SP = 9
private const val STACKED_STROKE_PADDING_PX = 4f

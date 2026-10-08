package com.stratum.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Compact single-series volume bar chart for dashboard cards (e.g. 7-day muscle volume).
 * Optional [dayLabels] (aligned with [series] by index) render as a quiet X-axis (e.g. M T W R F Sa Su).
 * Tap a bar to inspect its value. Horizontal pan is disabled so a parent pager can fling.
 *
 * @param series daily volume points; [TrendPointUi.weekIndex] is the X position.
 * @param dayLabels short weekday labels for each bar; empty omits the bottom axis.
 * @param barColor fill for all bars (defaults to theme accent).
 * @param fillHeight when true, skip the compact intrinsic height and fill the incoming constraints
 * (health pager). Cards keep the default sparkline height.
 * @param yScale when non-null, draw a quiet start axis labeled via [formatChartNumber] and range
 * the bars via [rangeProviderFor]. Null (default) keeps Vico's auto range with no axis.
 * @param contentDescription accessibility description.
 */
@Composable
fun StratumVolumeBarChart(
    series: List<TrendPointUi>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    dayLabels: List<String> = emptyList(),
    barColor: Color = Color.Unspecified,
    fillHeight: Boolean = false,
    yScale: DetailYScale? = null,
) {
    val colors = StratumThemeAccessor.colors
    val resolvedBar =
        if (barColor == Color.Unspecified) colors.accent else barColor

    if (series.isEmpty()) {
        VolumeBarBaselineOnly(
            modifier = modifier.semantics { this.contentDescription = contentDescription },
            color = colors.borderSubtle,
        )
        return
    }

    val column = rememberLineComponent(
        fill = Fill(resolvedBar),
        thickness = COLUMN_WIDTH_DP.dp,
    )
    val columnProvider = remember(column) {
        SingleColumnProvider(column)
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    val reporter = LocalUiRenderErrorReporter.current
    var modelFailed by remember(series) { mutableStateOf(false) }
    if (modelFailed) {
        VolumeBarBaselineOnly(
            modifier = modifier.semantics { this.contentDescription = contentDescription },
            color = colors.borderSubtle,
        )
        return
    }
    LaunchedEffect(series) {
        runCatchingChartModel(reporter, onFailure = { modelFailed = true }) {
            modelProducer.runTransaction {
                columnModel {
                    series(
                        x = series.map { it.weekIndex },
                        y = series.map { it.value },
                    )
                }
            }
        }
    }
    val inspectionModel =
        rememberInspectionChartModel(key = series) {
            CartesianChartModel(
                models =
                    listOf(
                        syncColumnLayerModel(
                            x = series.map { it.weekIndex.toDouble() },
                            y = series.map { it.value },
                        ),
                    ),
            )
        }

    val labelStyle = TextStyle(
        color = colors.textMuted,
        fontSize = AXIS_LABEL_SP.sp,
    )
    val axisLabel = rememberAxisLabelComponent(style = labelStyle)
    // Invisible chrome — labels only so the card stays quiet.
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
            val index = value.toInt()
            labelsForAxis.getOrNull(index).orEmpty()
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
    // A shown axis owns the Y range via the scale; otherwise keep Vico's auto.
    val columnRangeProvider = remember(yScale) {
        yScale?.let(::rangeProviderFor) ?: CartesianLayerRangeProvider.auto()
    }

    val marker = rememberStratumChartMarker(decimalCount = 0)
    val chart = rememberCartesianChart(
        rememberColumnCartesianLayer(
            columnProvider = columnProvider,
            columnCollectionSpacing = COLUMN_SPACING_DP.dp,
            rangeProvider = columnRangeProvider,
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
                    CHART_HEIGHT_WITH_LABELS_DP.dp
                } else {
                    CHART_HEIGHT_DP.dp
                },
            )
    }
    StratumCartesianChartHost(
        chart = chart,
        modelProducer = modelProducer,
        inspectionModel = inspectionModel,
        modifier = sizedModifier,
        contentDescription = contentDescription,
        scrollState = rememberStratumChartScrollState(),
    )
}

@Immutable
private class SingleColumnProvider(
    private val column: LineComponent,
) : ColumnCartesianLayer.ColumnProvider {
    override fun getColumn(
        entry: ColumnCartesianLayerModel.Entry,
        extraStore: ExtraStore,
    ): LineComponent = column

    override fun getWidestSeriesColumn(
        seriesKey: Any,
        seriesIndex: Int,
        extraStore: ExtraStore,
    ): LineComponent = column
}

@Composable
private fun VolumeBarBaselineOnly(modifier: Modifier, color: Color) {
    Canvas(modifier.fillMaxWidth().height(CHART_HEIGHT_DP.dp)) {
        val y = size.height / 2f
        drawLine(
            color = color,
            start = Offset(STROKE_PADDING_PX, y),
            end = Offset(size.width - STROKE_PADDING_PX, y),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

private const val CHART_HEIGHT_DP = 48
private const val CHART_HEIGHT_WITH_LABELS_DP = 62
private const val COLUMN_WIDTH_DP = 6
private const val COLUMN_SPACING_DP = 2
private const val AXIS_LABEL_SP = 9
private const val STROKE_PADDING_PX = 4f

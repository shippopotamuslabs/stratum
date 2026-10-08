package com.stratum.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisTickComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Compact shared-Y multi-line chart (up to four series). Unlike [StratumTrendChart], series are
 * **not** independently normalized — they share one scale so magnitudes stay comparable (e.g. hours
 * of Light vs Deep vs REM vs Awake sleep, or a body-weight trend). Optional [dayLabels] render as a
 * quiet X-axis. Points are tappable so a parent pager can show the value at that day.
 * Use [valueFormat] [CompactChartValueFormat.HoursMinutes] when Y is stored as hours.
 *
 * @param showYAxis when true, draw a quiet start axis labeled with [yScale] formatting. The line
 * layer already ranges via [yScale], so the axis only reveals the existing scale.
 */
@Composable
fun StratumMultiLineChart(
    primarySeries: List<TrendPointUi>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    secondarySeries: List<TrendPointUi> = emptyList(),
    tertiarySeries: List<TrendPointUi> = emptyList(),
    quaternarySeries: List<TrendPointUi> = emptyList(),
    primaryColor: Color = Color.Unspecified,
    secondaryColor: Color = Color.Unspecified,
    tertiaryColor: Color = Color.Unspecified,
    quaternaryColor: Color = Color.Unspecified,
    dayLabels: List<String> = emptyList(),
    yScale: DetailYScale = DetailYScale.Volume,
    valueFormat: CompactChartValueFormat = CompactChartValueFormat.Number,
    showYAxis: Boolean = false,
) {
    val colors = StratumThemeAccessor.colors
    val resolvedPrimary = if (primaryColor == Color.Unspecified) colors.textMuted else primaryColor
    val resolvedSecondary = if (secondaryColor == Color.Unspecified) colors.accent else secondaryColor
    val resolvedTertiary = if (tertiaryColor == Color.Unspecified) colors.textData else tertiaryColor
    val resolvedQuaternary = if (quaternaryColor == Color.Unspecified) colors.error else quaternaryColor

    val hasPrimary = primarySeries.size >= MIN_POINTS_FOR_LINE
    val hasSecondary = secondarySeries.size >= MIN_POINTS_FOR_LINE
    val hasTertiary = tertiarySeries.size >= MIN_POINTS_FOR_LINE
    val hasQuaternary = quaternarySeries.size >= MIN_POINTS_FOR_LINE
    val hasDrawableSeries = listOf(hasPrimary, hasSecondary, hasTertiary, hasQuaternary).any { it }
    if (!hasDrawableSeries) {
        MultiLineBaselineOnly(
            modifier = modifier.semantics { this.contentDescription = contentDescription },
            color = colors.borderSubtle,
        )
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    val reporter = LocalUiRenderErrorReporter.current
    val primaryValues = primarySeries.map { it.value }
    val secondaryValues = secondarySeries.map { it.value }
    val tertiaryValues = tertiarySeries.map { it.value }
    val quaternaryValues = quaternarySeries.map { it.value }
    var modelFailed by remember(primaryValues, secondaryValues, tertiaryValues, quaternaryValues) {
        mutableStateOf(false)
    }
    if (modelFailed) {
        MultiLineBaselineOnly(
            modifier = modifier.semantics { this.contentDescription = contentDescription },
            color = colors.borderSubtle,
        )
        return
    }
    LaunchedEffect(primaryValues, secondaryValues, tertiaryValues, quaternaryValues) {
        runCatchingChartModel(reporter, onFailure = { modelFailed = true }) {
            modelProducer.runTransaction {
                lineModel {
                    if (hasPrimary) {
                        series(
                            x = primarySeries.map { it.weekIndex },
                            y = primaryValues,
                        )
                    }
                    if (hasSecondary) {
                        series(
                            x = secondarySeries.map { it.weekIndex },
                            y = secondaryValues,
                        )
                    }
                    if (hasTertiary) {
                        series(
                            x = tertiarySeries.map { it.weekIndex },
                            y = tertiaryValues,
                        )
                    }
                    if (hasQuaternary) {
                        series(
                            x = quaternarySeries.map { it.weekIndex },
                            y = quaternaryValues,
                        )
                    }
                }
            }
        }
    }

    val primaryLine = rememberLineStyle(resolvedPrimary)
    val secondaryLine = rememberLineStyle(resolvedSecondary)
    val tertiaryLine = rememberLineStyle(resolvedTertiary)
    val quaternaryLine = rememberLineStyle(resolvedQuaternary)
    val lineStyles = buildList {
        if (hasPrimary) add(primaryLine)
        if (hasSecondary) add(secondaryLine)
        if (hasTertiary) add(tertiaryLine)
        if (hasQuaternary) add(quaternaryLine)
    }

    val labelStyle = TextStyle(
        color = colors.textMuted,
        fontSize = AXIS_LABEL_SP.sp,
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
    val yFormatter = remember(yScale) {
        CartesianValueFormatter { _, value, _ -> formatChartNumber(value, yScale) }
    }
    val yItemPlacer = remember {
        VerticalAxis.ItemPlacer.count(count = { Y_AXIS_LABEL_COUNT })
    }
    val startAxis = if (showYAxis) {
        VerticalAxis.rememberStart(
            line = invisibleLine,
            label = axisLabel,
            tick = invisibleTick,
            guideline = null,
            valueFormatter = yFormatter,
            itemPlacer = yItemPlacer,
        )
    } else {
        null
    }

    val lineProvider = LineCartesianLayer.LineProvider.series(lineStyles)
    val marker = rememberStratumChartMarker(
        decimalCount = 1,
        colorCode = lineStyles.size > 1,
        valueFormat = valueFormat,
    )
    val chart = rememberCartesianChart(
        rememberLineCartesianLayer(
            lineProvider = lineProvider,
            rangeProvider = rangeProviderFor(yScale),
        ),
        bottomAxis = bottomAxis,
        startAxis = startAxis,
        marker = marker,
        markerController = rememberStratumChartMarkerController(),
    )

    CartesianChartHost(
        chart = chart,
        modelProducer = modelProducer,
        scrollState = rememberStratumChartScrollState(),
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = contentDescription },
    )
}

@Composable
private fun rememberLineStyle(color: Color): LineCartesianLayer.Line {
    val pointComponent = rememberShapeComponent(
        fill = Fill(color),
        shape = CircleShape,
    )
    val pointProvider = remember(pointComponent) {
        LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.Point(component = pointComponent, size = POINT_SIZE_DP.dp),
        )
    }
    return LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(color)),
        stroke = LineCartesianLayer.LineStroke.Continuous(
            thickness = LINE_STROKE_WIDTH_DP.dp,
            cap = StrokeCap.Round,
        ),
        pointProvider = pointProvider,
    )
}

@Composable
private fun MultiLineBaselineOnly(modifier: Modifier, color: Color) {
    Canvas(modifier.fillMaxWidth().height(EMPTY_HEIGHT_DP.dp)) {
        val y = size.height / 2f
        drawLine(
            color = color,
            start = Offset(STROKE_PADDING_PX, y),
            end = Offset(size.width - STROKE_PADDING_PX, y),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

private const val LINE_STROKE_WIDTH_DP = 2
private const val POINT_SIZE_DP = 6
private const val AXIS_LABEL_SP = 9
private const val Y_AXIS_LABEL_COUNT = 4
private const val EMPTY_HEIGHT_DP = 48
private const val STROKE_PADDING_PX = 4f
private const val MIN_POINTS_FOR_LINE = 2

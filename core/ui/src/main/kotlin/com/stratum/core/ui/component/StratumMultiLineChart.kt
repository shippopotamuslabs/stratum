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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
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
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Compact shared-Y multi-line chart (up to three series). Unlike [StratumTrendChart], series are
 * **not** independently normalized — they share one scale so magnitudes stay comparable (e.g. hours
 * of Light vs Deep vs REM sleep). Optional [dayLabels] render as a quiet X-axis.
 */
@Composable
fun StratumMultiLineChart(
    primarySeries: List<TrendPointUi>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    secondarySeries: List<TrendPointUi> = emptyList(),
    tertiarySeries: List<TrendPointUi> = emptyList(),
    primaryColor: Color = Color.Unspecified,
    secondaryColor: Color = Color.Unspecified,
    tertiaryColor: Color = Color.Unspecified,
    dayLabels: List<String> = emptyList(),
) {
    val colors = StratumThemeAccessor.colors
    val resolvedPrimary = if (primaryColor == Color.Unspecified) colors.textMuted else primaryColor
    val resolvedSecondary = if (secondaryColor == Color.Unspecified) colors.accent else secondaryColor
    val resolvedTertiary = if (tertiaryColor == Color.Unspecified) colors.textData else tertiaryColor

    val hasPrimary = primarySeries.size >= MIN_POINTS_FOR_LINE
    val hasSecondary = secondarySeries.size >= MIN_POINTS_FOR_LINE
    val hasTertiary = tertiarySeries.size >= MIN_POINTS_FOR_LINE
    if (!hasPrimary && !hasSecondary && !hasTertiary) {
        MultiLineBaselineOnly(
            modifier = modifier.semantics { this.contentDescription = contentDescription },
            color = colors.borderSubtle,
        )
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    val primaryValues = primarySeries.map { it.value }
    val secondaryValues = secondarySeries.map { it.value }
    val tertiaryValues = tertiarySeries.map { it.value }
    LaunchedEffect(primaryValues, secondaryValues, tertiaryValues) {
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
            }
        }
    }

    val primaryLine = rememberLineStyle(resolvedPrimary)
    val secondaryLine = rememberLineStyle(resolvedSecondary)
    val tertiaryLine = rememberLineStyle(resolvedTertiary)
    val lineStyles = buildList {
        if (hasPrimary) add(primaryLine)
        if (hasSecondary) add(secondaryLine)
        if (hasTertiary) add(tertiaryLine)
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

    val lineProvider = when (lineStyles.size) {
        1 -> LineCartesianLayer.LineProvider.series(lineStyles[0])
        2 -> LineCartesianLayer.LineProvider.series(lineStyles[0], lineStyles[1])
        else -> LineCartesianLayer.LineProvider.series(lineStyles[0], lineStyles[1], lineStyles[2])
    }
    val chart = rememberCartesianChart(
        rememberLineCartesianLayer(
            lineProvider = lineProvider,
            rangeProvider = VolumeRangeProvider,
        ),
        bottomAxis = bottomAxis,
    )

    CartesianChartHost(
        chart = chart,
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = contentDescription },
    )
}

@Composable
private fun rememberLineStyle(color: Color): LineCartesianLayer.Line =
    LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(color)),
        stroke = LineCartesianLayer.LineStroke.Continuous(
            thickness = LINE_STROKE_WIDTH_DP.dp,
            cap = StrokeCap.Round,
        ),
    )

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
private const val AXIS_LABEL_SP = 9
private const val EMPTY_HEIGHT_DP = 48
private const val STROKE_PADDING_PX = 4f
private const val MIN_POINTS_FOR_LINE = 2

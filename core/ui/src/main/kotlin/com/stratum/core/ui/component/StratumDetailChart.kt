package com.stratum.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisTickComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
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
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import com.stratum.core.ui.theme.StratumThemeAccessor
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Fullscreen detail chart with real Y values and date X labels.
 *
 * **Progress** uses signed bars about zero (dashboard language). e1RM / volume use lines, including
 * a single-point marker when only one week exists.
 *
 * Dual Y remounts under [key] for stable Vico layer↔model pairing.
 */
@Composable
fun StratumDetailChart(
    primarySeries: List<DetailChartPoint>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    secondarySeries: List<DetailChartPoint>? = null,
    primaryColor: Color = Color.Unspecified,
    secondaryColor: Color = Color.Unspecified,
    primaryYScale: DetailYScale = DetailYScale.Weight,
    secondaryYScale: DetailYScale = DetailYScale.Volume,
) {
    val colors = StratumThemeAccessor.colors
    val resolvedPrimary =
        if (primaryColor == Color.Unspecified) {
            if (primaryYScale == DetailYScale.Progress) colors.setComplete else colors.textData
        } else {
            primaryColor
        }
    val resolvedSecondary =
        if (secondaryColor == Color.Unspecified) {
            colors.textMuted.copy(alpha = SECONDARY_ALPHA)
        } else {
            secondaryColor
        }

    // Progress is always signed bars; Volume-only primary uses unsigned bars (daily / weekly volume).
    // Dual mode keeps volume as a secondary line so strength/progress stays readable.
    val progressAsBars = primaryYScale == DetailYScale.Progress
    val volumeAsBars = primaryYScale == DetailYScale.Volume
    val primaryAsBars = progressAsBars || volumeAsBars
    // Single weekly point is valid (bars or marked line). Never promote secondary to primary.
    val hasPrimary = primarySeries.isNotEmpty()
    val hasSecondary = secondarySeries != null && secondarySeries.isNotEmpty()
    val dualMode = hasPrimary && hasSecondary

    if (!hasPrimary) {
        // Empty primary (e.g. Dual with no progress weeks) — honest empty, no silent volume swap.
        DetailBaselineOnly(
            modifier = modifier.semantics { this.contentDescription = contentDescription },
            color = colors.borderSubtle,
        )
        return
    }

    val structureKey = when {
        progressAsBars && dualMode -> "progress+volume"
        volumeAsBars && dualMode -> "volume-bars+line"
        primaryAsBars -> "primary-bars"
        dualMode -> "dual-line"
        else -> "single-line"
    }

    key(structureKey) {
        DetailChartContent(
            primarySeries = primarySeries,
            secondarySeries = if (dualMode) secondarySeries else null,
            primaryColor = resolvedPrimary,
            secondaryColor = resolvedSecondary,
            primaryYScale = primaryYScale,
            secondaryYScale = secondaryYScale,
            dualMode = dualMode,
            progressAsBars = progressAsBars,
            primaryAsBars = primaryAsBars,
            contentDescription = contentDescription,
            modifier = modifier,
        )
    }
}

@Composable
private fun DetailChartContent(
    primarySeries: List<DetailChartPoint>,
    secondarySeries: List<DetailChartPoint>?,
    primaryColor: Color,
    secondaryColor: Color,
    primaryYScale: DetailYScale,
    secondaryYScale: DetailYScale,
    dualMode: Boolean,
    progressAsBars: Boolean,
    primaryAsBars: Boolean,
    contentDescription: String,
    modifier: Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val modelProducer = remember { CartesianChartModelProducer() }
    val secondaryValues = secondarySeries.orEmpty()

    val positiveBarColor = primaryColor
    val negativeBarColor = lerp(primaryColor, colors.textPrimary, INDEX_NEGATIVE_LIGHTEN_FRACTION)

    LaunchedEffect(primarySeries, secondaryValues, dualMode, primaryAsBars) {
        modelProducer.runTransaction {
            if (primaryAsBars) {
                columnModel {
                    series(
                        x = primarySeries.map { it.xEpochDay.toDouble() },
                        y = primarySeries.map { it.value },
                    )
                }
            } else {
                lineModel {
                    series(
                        x = primarySeries.map { it.xEpochDay.toDouble() },
                        y = primarySeries.map { it.value },
                    )
                }
            }
            if (dualMode) {
                lineModel {
                    series(
                        x = secondaryValues.map { it.xEpochDay.toDouble() },
                        y = secondaryValues.map { it.value },
                    )
                }
            }
        }
    }

    val labelStyle = TextStyle(
        color = colors.textMuted,
        fontSize = AXIS_LABEL_SP.sp,
    )
    val axisLabel = rememberAxisLabelComponent(style = labelStyle)
    val axisLine = rememberAxisLineComponent(
        fill = Fill(colors.borderSubtle),
        thickness = AXIS_LINE_THICKNESS_DP.dp,
    )
    val axisTick = rememberAxisTickComponent(
        fill = Fill(colors.borderSubtle),
        thickness = AXIS_LINE_THICKNESS_DP.dp,
    )
    val guideline = rememberAxisGuidelineComponent(
        fill = Fill(colors.borderSubtle.copy(alpha = GUIDELINE_ALPHA)),
        thickness = GUIDELINE_THICKNESS_DP.dp,
    )
    val endGuideline = rememberLineComponent(
        fill = Fill(Color.Transparent),
        thickness = 0.dp,
    )
    val progressZeroLine = rememberLineComponent(
        fill = Fill(colors.textMuted.copy(alpha = ZERO_LINE_ALPHA)),
        thickness = ZERO_LINE_THICKNESS_DP.dp,
    )

    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    }
    // Week starts are Monday 00:00 UTC — format in UTC so labels don't slip to Sunday locally.
    val xFormatter = remember(dateFormatter) {
        CartesianValueFormatter { _, value, _ ->
            val day = value.toLong()
            val instant = Instant.ofEpochSecond(day * SECONDS_PER_DAY)
            dateFormatter.format(instant.atZone(ZoneOffset.UTC))
        }
    }
    val primaryYFormatter = remember(primaryYScale) {
        CartesianValueFormatter { _, value, _ -> formatChartNumber(value, primaryYScale) }
    }
    val secondaryYFormatter = remember(secondaryYScale) {
        CartesianValueFormatter { _, value, _ -> formatChartNumber(value, secondaryYScale) }
    }
    val yItemPlacer = remember {
        VerticalAxis.ItemPlacer.count(count = { Y_AXIS_MAX_LABELS })
    }
    val primaryRangeProvider = remember(primaryYScale) {
        rangeProviderFor(primaryYScale)
    }
    val secondaryRangeProvider = remember(secondaryYScale) {
        rangeProviderFor(secondaryYScale)
    }

    val positiveColumn = rememberLineComponent(
        fill = Fill(positiveBarColor),
        thickness = DETAIL_COLUMN_WIDTH_DP.dp,
    )
    val negativeColumn = rememberLineComponent(
        fill = Fill(negativeBarColor),
        thickness = DETAIL_COLUMN_WIDTH_DP.dp,
    )
    val columnProvider = remember(positiveColumn, negativeColumn) {
        DetailSignedColumnProvider(positive = positiveColumn, negative = negativeColumn)
    }

    val primaryPointComponent = rememberShapeComponent(
        fill = Fill(primaryColor),
        shape = CircleShape,
    )
    val secondaryPointComponent = rememberShapeComponent(
        fill = Fill(secondaryColor),
        shape = CircleShape,
    )
    // Always show points so a single-week series is visible (line stroke alone needs 2+ points).
    val primaryPointProvider = remember(primaryPointComponent) {
        LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.Point(component = primaryPointComponent, size = POINT_SIZE_DP.dp),
        )
    }
    val secondaryPointProvider = remember(secondaryPointComponent) {
        LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.Point(component = secondaryPointComponent, size = POINT_SIZE_DP.dp),
        )
    }

    val columnLayer = rememberColumnCartesianLayer(
        columnProvider = columnProvider,
        columnCollectionSpacing = DETAIL_COLUMN_SPACING_DP.dp,
        rangeProvider = primaryRangeProvider,
        verticalAxisPosition = Axis.Position.Vertical.Start,
    )
    val primaryLineLayer = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(
            LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(Fill(primaryColor)),
                stroke = LineCartesianLayer.LineStroke.Continuous(
                    thickness = LINE_STROKE_DP.dp,
                    cap = StrokeCap.Round,
                ),
                pointProvider = primaryPointProvider,
            ),
        ),
        rangeProvider = primaryRangeProvider,
        verticalAxisPosition = Axis.Position.Vertical.Start,
    )
    val secondaryLineLayer = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(
            LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(Fill(secondaryColor)),
                stroke = LineCartesianLayer.LineStroke.Continuous(
                    thickness = LINE_STROKE_DP.dp,
                    cap = StrokeCap.Round,
                ),
                pointProvider = secondaryPointProvider,
            ),
        ),
        rangeProvider = secondaryRangeProvider,
        verticalAxisPosition = Axis.Position.Vertical.End,
    )

    // Zero baseline only for signed progress index; volume bars grow from the chart floor.
    val decorations = remember(progressAsBars, progressZeroLine) {
        if (progressAsBars) {
            listOf(
                HorizontalLine(
                    y = { 0.0 },
                    line = progressZeroLine,
                    labelComponent = null,
                    label = { "" },
                    verticalAxisPosition = Axis.Position.Vertical.Start,
                ),
            )
        } else {
            emptyList()
        }
    }

    val startAxis = VerticalAxis.rememberStart(
        line = axisLine,
        label = axisLabel,
        tick = axisTick,
        guideline = guideline,
        valueFormatter = primaryYFormatter,
        itemPlacer = yItemPlacer,
    )
    val endAxis = VerticalAxis.rememberEnd(
        line = axisLine,
        label = axisLabel,
        tick = axisTick,
        guideline = endGuideline,
        valueFormatter = secondaryYFormatter,
        itemPlacer = yItemPlacer,
    )
    val bottomAxis = HorizontalAxis.rememberBottom(
        line = axisLine,
        label = axisLabel,
        tick = axisTick,
        guideline = null,
        valueFormatter = xFormatter,
    )

    val chart = when {
        primaryAsBars && dualMode -> rememberCartesianChart(
            columnLayer,
            secondaryLineLayer,
            startAxis = startAxis,
            endAxis = endAxis,
            bottomAxis = bottomAxis,
            decorations = decorations,
        )
        primaryAsBars -> rememberCartesianChart(
            columnLayer,
            startAxis = startAxis,
            bottomAxis = bottomAxis,
            decorations = decorations,
        )
        dualMode -> rememberCartesianChart(
            primaryLineLayer,
            secondaryLineLayer,
            startAxis = startAxis,
            endAxis = endAxis,
            bottomAxis = bottomAxis,
            decorations = decorations,
        )
        else -> rememberCartesianChart(
            primaryLineLayer,
            startAxis = startAxis,
            bottomAxis = bottomAxis,
            decorations = decorations,
        )
    }

    CartesianChartHost(
        chart = chart,
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxSize()
            .semantics { this.contentDescription = contentDescription },
    )
}

/** Value domain for Y-axis label formatting, range policy, and mark type. */
enum class DetailYScale {
    Weight,
    Volume,
    /** Baseline-relative log index — rendered as signed bars about zero. */
    Progress,
}

/** One point on a [StratumDetailChart]. [xEpochDay] is UTC epoch day for the week start. */
data class DetailChartPoint(
    val xEpochDay: Long,
    val value: Float,
)

@Immutable
private class DetailSignedColumnProvider(
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

/** @deprecated Use [formatChartNumber] — kept for existing tests during transition. */
internal fun formatDetailAxisY(value: Double, scale: DetailYScale): String =
    formatChartNumber(value, scale)

internal fun rangeProviderFor(scale: DetailYScale): CartesianLayerRangeProvider = when (scale) {
    DetailYScale.Weight -> CartesianLayerRangeProvider.Intrinsic
    DetailYScale.Volume -> CartesianLayerRangeProvider.auto()
    DetailYScale.Progress -> ProgressBaselineRangeProvider
}

internal object ProgressBaselineRangeProvider : CartesianLayerRangeProvider {
    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
        progressYRange(minY, maxY).first

    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
        progressYRange(minY, maxY).second
}

internal fun progressYRange(dataMin: Double, dataMax: Double): Pair<Double, Double> {
    val baseMin = min(dataMin, 0.0)
    val baseMax = max(dataMax, 0.0)
    val rawSpan = baseMax - baseMin
    val halfSpan = max(rawSpan, PROGRESS_MIN_SPAN) / 2.0
    val center = (baseMin + baseMax) / 2.0
    val expandedMin = min(baseMin, center - halfSpan)
    val expandedMax = max(baseMax, center + halfSpan)
    val pad = (expandedMax - expandedMin) * PROGRESS_PAD_FRACTION
    return (expandedMin - pad) to (expandedMax + pad)
}

@Composable
private fun DetailBaselineOnly(modifier: Modifier, color: Color) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(EMPTY_HEIGHT_DP.dp),
    ) {
        val y = size.height / 2f
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

private const val LINE_STROKE_DP = 2.5f
private const val POINT_SIZE_DP = 6f
private const val DETAIL_COLUMN_WIDTH_DP = 6f
private const val DETAIL_COLUMN_SPACING_DP = 2f
private const val AXIS_LINE_THICKNESS_DP = 1f
private const val GUIDELINE_THICKNESS_DP = 1f
private const val ZERO_LINE_THICKNESS_DP = 1.5f
private const val GUIDELINE_ALPHA = 0.35f
private const val ZERO_LINE_ALPHA = 0.75f
private const val SECONDARY_ALPHA = 0.55f
private const val AXIS_LABEL_SP = 11
private const val EMPTY_HEIGHT_DP = 120
private const val SECONDS_PER_DAY = 86_400L
private const val Y_AXIS_MAX_LABELS = 5
private const val PROGRESS_MIN_SPAN = 0.02
private const val PROGRESS_PAD_FRACTION = 0.08
private const val INDEX_NEGATIVE_LIGHTEN_FRACTION = 0.55f

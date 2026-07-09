package com.stratum.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import com.stratum.core.ui.theme.StratumThemeAccessor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Fullscreen detail chart with real Y values and date X labels.
 *
 * **Progress** ([DetailYScale.Progress]) uses **signed bars about zero** — same visual language as
 * the dashboard [StratumIndexVolumeChart] — so above/below early baseline is obvious. e1RM and
 * volume stay as lines.
 *
 * Dual Y remounts under [key] so layer↔model pairing stays stable (avoids Vico
 * `Key null is missing in the map` when switching single ↔ dual).
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

    val progressAsBars = primaryYScale == DetailYScale.Progress
    // Progress bars need only one point; lines need two.
    val hasPrimary = if (progressAsBars) {
        primarySeries.isNotEmpty()
    } else {
        primarySeries.size >= MIN_POINTS_FOR_LINE
    }
    val hasSecondary = secondarySeries != null && secondarySeries.size >= MIN_POINTS_FOR_LINE
    val dualMode = hasPrimary && hasSecondary

    if (!hasPrimary && !hasSecondary) {
        DetailBaselineOnly(
            modifier = modifier.semantics { this.contentDescription = contentDescription },
            color = colors.borderSubtle,
        )
        return
    }

    val effectivePrimary = when {
        hasPrimary -> primarySeries
        else -> secondarySeries.orEmpty()
    }
    val effectivePrimaryColor = when {
        hasPrimary -> resolvedPrimary
        else -> resolvedSecondary
    }
    val effectivePrimaryScale = when {
        hasPrimary -> primaryYScale
        else -> secondaryYScale
    }
    // Structure id for key(): bars-only / bars+line / line-only / dual-line.
    val structureKey = when {
        effectivePrimaryScale == DetailYScale.Progress && dualMode -> "progress+volume"
        effectivePrimaryScale == DetailYScale.Progress -> "progress"
        dualMode -> "dual-line"
        else -> "single-line"
    }

    key(structureKey) {
        DetailChartContent(
            primarySeries = effectivePrimary,
            secondarySeries = if (dualMode) secondarySeries.orEmpty() else null,
            primaryColor = effectivePrimaryColor,
            secondaryColor = resolvedSecondary,
            primaryYScale = effectivePrimaryScale,
            secondaryYScale = secondaryYScale,
            dualMode = dualMode,
            progressAsBars = effectivePrimaryScale == DetailYScale.Progress,
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
    contentDescription: String,
    modifier: Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val modelProducer = remember { CartesianChartModelProducer() }
    val secondaryValues = secondarySeries.orEmpty()

    // Signed progress colors match dashboard index bars.
    val positiveBarColor = if (progressAsBars) {
        primaryColor
    } else {
        colors.setComplete
    }
    val negativeBarColor = if (progressAsBars) {
        lerp(primaryColor, colors.textPrimary, INDEX_NEGATIVE_LIGHTEN_FRACTION)
    } else {
        primaryColor
    }

    LaunchedEffect(primarySeries, secondaryValues, dualMode, progressAsBars) {
        modelProducer.runTransaction {
            if (progressAsBars) {
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
    val zone = remember { ZoneId.systemDefault() }
    val xFormatter = remember(dateFormatter, zone) {
        CartesianValueFormatter { _, value, _ ->
            val day = value.toLong()
            val instant = Instant.ofEpochSecond(day * SECONDS_PER_DAY)
            dateFormatter.format(instant.atZone(zone))
        }
    }
    val primaryYFormatter = remember(primaryYScale) {
        CartesianValueFormatter { _, value, _ -> formatDetailAxisY(value, primaryYScale) }
    }
    val secondaryYFormatter = remember(secondaryYScale) {
        CartesianValueFormatter { _, value, _ -> formatDetailAxisY(value, secondaryYScale) }
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
            ),
        ),
        rangeProvider = secondaryRangeProvider,
        verticalAxisPosition = Axis.Position.Vertical.End,
    )

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

    // structure is fixed inside key(...); branch is stable for this composition subtree.
    val chart = when {
        progressAsBars && dualMode -> rememberCartesianChart(
            columnLayer,
            secondaryLineLayer,
            startAxis = startAxis,
            endAxis = endAxis,
            bottomAxis = bottomAxis,
            decorations = decorations,
        )
        progressAsBars -> rememberCartesianChart(
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

/** Positive progress bars use [positive]; below-baseline bars use [negative]. */
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

/**
 * Compact Y labels: avoids "-0", repeated 0.1-style stamps, and progress indexes that need more
 * than one decimal place.
 */
internal fun formatDetailAxisY(value: Double, scale: DetailYScale): String {
    if (abs(value) < ZERO_EPSILON) return "0"
    return when (scale) {
        DetailYScale.Weight -> {
            if (abs(value) >= WEIGHT_INTEGER_THRESHOLD) {
                value.toInt().toString()
            } else {
                trimTrailingZeros(String.format(Locale.US, "%.1f", value))
            }
        }
        DetailYScale.Volume -> {
            if (abs(value - value.toLong()) < ZERO_EPSILON) {
                value.toLong().toString()
            } else {
                trimTrailingZeros(String.format(Locale.US, "%.1f", value))
            }
        }
        DetailYScale.Progress -> {
            val magnitude = abs(value)
            val raw = when {
                magnitude >= PROGRESS_THRESHOLD_ONES -> String.format(Locale.US, "%.2f", value)
                magnitude >= PROGRESS_THRESHOLD_TENTHS -> String.format(Locale.US, "%.2f", value)
                magnitude >= PROGRESS_THRESHOLD_HUNDREDTHS -> String.format(Locale.US, "%.3f", value)
                magnitude >= PROGRESS_THRESHOLD_THOUSANDTHS -> String.format(Locale.US, "%.4f", value)
                else -> String.format(Locale.US, "%.2e", value)
            }
            if (raw.contains('e', ignoreCase = true)) raw else trimTrailingZeros(raw)
        }
    }
}

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

private fun trimTrailingZeros(formatted: String): String =
    formatted.trimEnd('0').trimEnd('.')

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

private const val MIN_POINTS_FOR_LINE = 2
private const val LINE_STROKE_DP = 2.5f
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
private const val ZERO_EPSILON = 1e-12
private const val WEIGHT_INTEGER_THRESHOLD = 100.0
private const val PROGRESS_THRESHOLD_ONES = 1.0
private const val PROGRESS_THRESHOLD_TENTHS = 0.1
private const val PROGRESS_THRESHOLD_HUNDREDTHS = 0.01
private const val PROGRESS_THRESHOLD_THOUSANDTHS = 0.001
private const val PROGRESS_MIN_SPAN = 0.02
private const val PROGRESS_PAD_FRACTION = 0.08
private const val INDEX_NEGATIVE_LIGHTEN_FRACTION = 0.55f

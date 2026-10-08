package com.stratum.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLocale
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
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

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

    // Drop non-finite Y values — Vico axis math divides by yRange.length and crashes on NaN rects.
    val safePrimary = remember(primarySeries) { primarySeries.filter { it.value.isFinite() } }
    val safeSecondary = remember(secondarySeries) {
        secondarySeries?.filter { it.value.isFinite() }
    }

    // Progress is always signed bars; Volume-only primary uses unsigned bars (daily / weekly volume).
    // Dual mode keeps volume as a secondary line so strength/progress stays readable.
    val progressAsBars = primaryYScale == DetailYScale.Progress
    val volumeAsBars = primaryYScale == DetailYScale.Volume
    val primaryAsBars = progressAsBars || volumeAsBars
    // Single weekly point is valid (bars or marked line). Never promote secondary to primary.
    val hasPrimary = safePrimary.isNotEmpty()
    val hasSecondary = safeSecondary != null && safeSecondary.isNotEmpty()
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
            primarySeries = safePrimary,
            secondarySeries = if (dualMode) safeSecondary else null,
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
    val reporter = LocalUiRenderErrorReporter.current
    val secondaryValues = secondarySeries.orEmpty()
    var modelFailed by remember(primarySeries, secondaryValues, dualMode, primaryAsBars) {
        mutableStateOf(false)
    }
    if (modelFailed) {
        DetailBaselineOnly(
            modifier = modifier.semantics { this.contentDescription = contentDescription },
            color = colors.borderSubtle,
        )
        return
    }

    val positiveBarColor = primaryColor
    val negativeBarColor = lerp(primaryColor, colors.textPrimary, INDEX_NEGATIVE_LIGHTEN_FRACTION)

    LaunchedEffect(primarySeries, secondaryValues, dualMode, primaryAsBars) {
        runCatchingChartModel(reporter, onFailure = { modelFailed = true }) {
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

    // Observable read: the formatter rebuilds if the system locale changes.
    val locale = LocalLocale.current
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofPattern("MMM d", locale.platformLocale)
    }
    // X is a pure epoch-day integer (LocalDate.toEpochDay). Format via UTC midnight of that
    // proleptic day so weekly Monday-UTC and local daily keys both label the intended calendar date.
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
    /** Hero step counts — floored at 0 with a nice ceiling and compact "k" labels. */
    Steps,
}

/**
 * One point on a [StratumDetailChart].
 * [xEpochDay] is [java.time.LocalDate.toEpochDay] for the series' calendar day (UTC Monday for
 * weekly series; device-local calendar day for daily volume).
 */
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

/**
 * Y-range policy per metric. **Never returns a zero-length range** — Vico maps canvas Y with
 * `(value - minY) / length` and throws `IllegalStateException: Invalid rectangle` when length is 0
 * or NaN (common with a single week of identical e1RM / flat volume after Intrinsic or bad auto).
 */
internal fun rangeProviderFor(scale: DetailYScale): CartesianLayerRangeProvider = when (scale) {
    DetailYScale.Weight -> WeightRangeProvider
    DetailYScale.Volume -> VolumeRangeProvider
    DetailYScale.Progress -> ProgressBaselineRangeProvider
    DetailYScale.Steps -> StepsRangeProvider
}

/**
 * Compact-axis tick count per scale. Steps ceilings are 2× a nice step, so 3 evenly-spaced ticks
 * always land round ("0 / 5k / 10k"); other fitted scales use 4.
 */
internal fun yTickCountFor(scale: DetailYScale): Int = when (scale) {
    DetailYScale.Steps -> COMPACT_STEPS_LABEL_COUNT
    DetailYScale.Weight, DetailYScale.Volume, DetailYScale.Progress -> COMPACT_LABEL_COUNT
}

internal object ProgressBaselineRangeProvider : CartesianLayerRangeProvider {
    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
        progressYRange(minY, maxY).first

    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
        progressYRange(minY, maxY).second
}

internal object WeightRangeProvider : CartesianLayerRangeProvider {
    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
        weightYRange(minY, maxY).first

    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
        weightYRange(minY, maxY).second
}

internal object VolumeRangeProvider : CartesianLayerRangeProvider {
    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
        volumeYRange(minY, maxY).first

    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
        volumeYRange(minY, maxY).second
}

internal object StepsRangeProvider : CartesianLayerRangeProvider {
    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
        stepsYRange(minY, maxY).first

    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
        stepsYRange(minY, maxY).second
}

/**
 * Signed progress index about zero. Always includes 0 and a minimum span so flat near-zero weeks
 * still draw axis ticks without dividing by zero.
 */
internal fun progressYRange(dataMin: Double, dataMax: Double): Pair<Double, Double> {
    val (lo, hi) = finiteDataBounds(dataMin, dataMax, fallbackSpan = PROGRESS_MIN_SPAN)
    val baseMin = min(lo, 0.0)
    val baseMax = max(hi, 0.0)
    val rawSpan = baseMax - baseMin
    val halfSpan = max(rawSpan, PROGRESS_MIN_SPAN) / 2.0
    val center = (baseMin + baseMax) / 2.0
    val expandedMin = min(baseMin, center - halfSpan)
    val expandedMax = max(baseMax, center + halfSpan)
    val pad = (expandedMax - expandedMin) * PROGRESS_PAD_FRACTION
    return (expandedMin - pad) to (expandedMax + pad)
}

/**
 * e1RM / weight. Pads and expands flat series (two sessions at the same load collapse to one
 * week) so Intrinsic-style min==max never reaches Vico's axis mapper.
 */
internal fun weightYRange(dataMin: Double, dataMax: Double): Pair<Double, Double> {
    val (lo, hi) = finiteDataBounds(dataMin, dataMax, fallbackSpan = WEIGHT_MIN_SPAN)
    val span = max(hi - lo, WEIGHT_MIN_SPAN)
    val center = (lo + hi) / 2.0
    val half = span / 2.0
    val expandedMin = min(lo, center - half)
    val expandedMax = max(hi, center + half)
    // Keep floor non-negative when all data is positive (typical e1RM); still pad so ticks exist.
    val floor = if (expandedMin >= 0.0) max(0.0, expandedMin - span * WEIGHT_PAD_FRACTION) else {
        expandedMin - span * WEIGHT_PAD_FRACTION
    }
    val ceiling = expandedMax + span * WEIGHT_PAD_FRACTION
    return floor to max(ceiling, floor + WEIGHT_MIN_SPAN)
}

/**
 * Hard-set volume bars/lines. Floor at 0; ensure a positive ceiling even when every bar is 0
 * (zero-filled rest week) so VerticalAxis never sees length 0.
 */
internal fun volumeYRange(dataMin: Double, dataMax: Double): Pair<Double, Double> {
    val (_, hi) = finiteDataBounds(dataMin, dataMax, fallbackSpan = VOLUME_MIN_SPAN)
    val ceiling = max(hi, VOLUME_MIN_SPAN)
    val pad = max(ceiling * VOLUME_PAD_FRACTION, VOLUME_MIN_SPAN * VOLUME_PAD_FRACTION)
    return 0.0 to (ceiling + pad)
}

/**
 * Hero Steps bars. Floor at 0; ceiling is twice the smallest nice tick step covering half the
 * peak, so the 3 evenly-spaced axis ticks always land on round labels ("0 / 5k / 10k").
 * Guarantees positive length for all-zero / flat / non-finite input.
 */
internal fun stepsYRange(dataMin: Double, dataMax: Double): Pair<Double, Double> {
    val (_, hi) = finiteDataBounds(dataMin, dataMax, fallbackSpan = STEPS_MIN_SPAN)
    val peak = max(hi, 0.0)
    val step = niceStepsTickStep(max(peak / 2.0, STEPS_MIN_TICK_STEP))
    return 0.0 to (step * 2.0)
}

/** Smallest 1/2/2.5/5 × 10ⁿ step at or above [value] ([value] must be positive). */
internal fun niceStepsTickStep(value: Double): Double {
    val base = 10.0.pow(floor(log10(value)))
    for (multiple in NICE_STEP_MULTIPLES) {
        val candidate = multiple * base
        if (candidate >= value) return candidate
    }
    return 10.0 * base
}

/** Coerce raw model min/max to a finite ordered pair with at least [fallbackSpan] width. */
internal fun finiteDataBounds(
    dataMin: Double,
    dataMax: Double,
    fallbackSpan: Double,
): Pair<Double, Double> {
    val loRaw = if (dataMin.isFinite()) dataMin else 0.0
    val hiRaw = if (dataMax.isFinite()) dataMax else loRaw
    val lo = min(loRaw, hiRaw)
    val hi = max(loRaw, hiRaw)
    if (hi - lo >= fallbackSpan * 1e-9) return lo to hi
    // Flat / empty / non-finite — expand symmetrically around the point (or 0).
    val center = if (lo.isFinite()) lo else 0.0
    val half = fallbackSpan / 2.0
    return (center - half) to (center + half)
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
/** Minimum e1RM axis span (kg) so a single flat week still has non-zero length. */
private const val WEIGHT_MIN_SPAN = 5.0
private const val WEIGHT_PAD_FRACTION = 0.08
/** Minimum hard-set axis span so all-zero volume (rest week) still draws ticks. */
private const val VOLUME_MIN_SPAN = 1.0
/** Minimum step-data span for finite bounds; the tick floor below dominates the ceiling. */
private const val STEPS_MIN_SPAN = 1.0
/** Compact-axis labels for Steps (0 / step / 2×step) vs other scales. */
private const val COMPACT_STEPS_LABEL_COUNT = 3
private const val COMPACT_LABEL_COUNT = 4
/** Smallest half-range tick step, so all-zero / tiny step weeks still draw ticks (0/50/100). */
private const val STEPS_MIN_TICK_STEP = 50.0
private val NICE_STEP_MULTIPLES = doubleArrayOf(1.0, 2.0, 2.5, 5.0, 10.0)
private const val VOLUME_PAD_FRACTION = 0.08
private const val INDEX_NEGATIVE_LIGHTEN_FRACTION = 0.55f

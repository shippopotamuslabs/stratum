package com.stratum

import com.stratum.core.ui.component.DetailChartPoint
import com.stratum.core.ui.component.DetailYScale
import com.stratum.core.ui.component.dayStartToEpochDay
import com.stratum.core.ui.component.formatChartNumber
import com.stratum.core.ui.component.formatDayStartRangeLabel
import com.stratum.core.ui.component.formatWeekStartRangeLabel
import com.stratum.core.ui.component.weekStartToEpochDay
import com.stratum.domain.dashboard.GraphDetailKind
import com.stratum.domain.dashboard.GraphDetailSeries
import com.stratum.domain.dashboard.GraphDetailStats
import com.stratum.domain.dashboard.GraphDetailTimeRange
import com.stratum.domain.dashboard.TrendPoint
import com.stratum.domain.dashboard.filterTrendPointsByRange
import com.stratum.domain.dashboard.graphDetailStats
import com.stratum.domain.dashboard.graphDetailStatsNonZeroDays
import com.stratum.domain.dashboard.windowStart
import com.stratum.domain.dashboard.zeroFillDailyTrendPoints
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightConversion
import com.stratum.domain.lift.shortLabel
import com.stratum.domain.response.modeling.MuscleDailyVolumeBuilder
import java.time.Instant
import java.time.ZoneId

/** Fullscreen graph detail presentation state — no domain types reach composables. */
sealed interface GraphDetailUiState {
    data object Hidden : GraphDetailUiState
    data object Loading : GraphDetailUiState
    data object Empty : GraphDetailUiState
    data object Error : GraphDetailUiState
    data class Ready(
        val title: String,
        val kind: GraphDetailKindUi,
        /** Display modes for tabs (Primary | Volume | Dual). Empty when kind is volume-only. */
        val modes: List<GraphViewMode>,
        val selectedMode: GraphViewMode,
        val selectedRange: GraphTimeRangeUi,
        val ranges: List<GraphTimeRangeUi>,
        val stats: GraphStatsUi,
        /** Chart-ready points (epoch day X — UTC for weekly, local for daily volume). */
        val primaryPoints: List<DetailChartPoint>,
        val secondaryPoints: List<DetailChartPoint>?,
        val primaryColorRole: GraphSeriesColorRole,
        val secondaryColorRole: GraphSeriesColorRole?,
        val primaryYScale: DetailYScale,
        val secondaryYScale: DetailYScale?,
    ) : GraphDetailUiState
}

enum class GraphDetailKindUi { Lift, Muscle, MuscleDailyVolume }

/**
 * Chart view mode — orthogonal to subject kind.
 * - [Primary]: e1RM (lift) or progress index (muscle)
 * - [Volume]: hard sets only
 * - [Dual]: primary + volume overlay
 */
enum class GraphViewMode {
    Primary,
    Volume,
    Dual,
}

enum class GraphTimeRangeUi {
    OneWeek,
    OneMonth,
    ThreeMonths,
    SixMonths,
    OneYear,
    All,
}

enum class GraphSeriesColorRole {
    Strength,
    Progress,
    Volume,
    /** Accent bars for daily hard-set volume (7-Day tab). */
    DailyVolume,
}

data class GraphStatsUi(
    val averageText: String,
    val bestText: String,
    val averageLabelKey: GraphStatLabelKey,
    val bestLabelKey: GraphStatLabelKey,
    val dateRangeText: String,
    val unitSuffix: String,
)

enum class GraphStatLabelKey {
    E1rm,
    Volume,
    Progress,
    Steps,
    Vitals,
}

/**
 * Full-history session; filter client-side without reloading.
 * [zone] is the calendar zone used when daily volume was bucketed — reuse for 1W zero-fill,
 * epoch-day X, and date-range labels so presentation cannot drift from load.
 */
internal data class GraphDetailSession(
    val title: String,
    val kind: GraphDetailKindUi,
    val strengthOrProgress: List<TrendPoint>,
    val volume: List<TrendPoint>,
    val selectedMode: GraphViewMode,
    val selectedRange: GraphTimeRangeUi,
    val zone: ZoneId,
)

internal fun GraphDetailSeries.toSession(zone: ZoneId): GraphDetailSession {
    val kindUi = when (kind) {
        GraphDetailKind.Lift -> GraphDetailKindUi.Lift
        GraphDetailKind.Muscle -> GraphDetailKindUi.Muscle
        GraphDetailKind.MuscleDailyVolume -> GraphDetailKindUi.MuscleDailyVolume
    }
    val defaultMode = when (kindUi) {
        GraphDetailKindUi.Lift -> GraphViewMode.Primary
        GraphDetailKindUi.Muscle ->
            if (primary.isNotEmpty()) GraphViewMode.Primary else GraphViewMode.Volume
        GraphDetailKindUi.MuscleDailyVolume -> GraphViewMode.Volume
    }
    val defaultRange = when (kindUi) {
        GraphDetailKindUi.MuscleDailyVolume -> GraphTimeRangeUi.OneWeek
        GraphDetailKindUi.Lift, GraphDetailKindUi.Muscle -> GraphTimeRangeUi.ThreeMonths
    }
    return GraphDetailSession(
        title = title,
        kind = kindUi,
        strengthOrProgress = primary,
        volume = when (kindUi) {
            // Daily volume series stores hard sets in primary; keep volume alias in sync.
            GraphDetailKindUi.MuscleDailyVolume -> primary
            GraphDetailKindUi.Lift, GraphDetailKindUi.Muscle -> secondary
        },
        selectedMode = defaultMode,
        selectedRange = defaultRange,
        zone = zone,
    )
}

internal fun modesFor(kind: GraphDetailKindUi): List<GraphViewMode> = when (kind) {
    GraphDetailKindUi.MuscleDailyVolume -> listOf(GraphViewMode.Volume)
    GraphDetailKindUi.Lift, GraphDetailKindUi.Muscle ->
        listOf(GraphViewMode.Primary, GraphViewMode.Volume, GraphViewMode.Dual)
}

internal fun rangesFor(kind: GraphDetailKindUi): List<GraphTimeRangeUi> = when (kind) {
    GraphDetailKindUi.MuscleDailyVolume -> GraphTimeRangeUi.entries.toList()
    // Weekly series: 1W is a single bucket — omit.
    GraphDetailKindUi.Lift, GraphDetailKindUi.Muscle ->
        GraphTimeRangeUi.entries.filter { it != GraphTimeRangeUi.OneWeek }
}

internal fun GraphDetailSession.toReadyUi(
    now: Instant = Instant.now(),
    displayUnit: Weight.WeightUnit = Weight.WeightUnit.Lb,
): GraphDetailUiState.Ready {
    val modes = modesFor(kind)
    val mode = selectedMode.takeIf { it in modes } ?: modes.first()
    val availableRanges = rangesFor(kind)
    val range = selectedRange.takeIf { it in availableRanges }
        ?: availableRanges.firstOrNull()
        ?: GraphTimeRangeUi.ThreeMonths
    val since = range.toDomain().windowStart(now)
    val dual = mode == GraphViewMode.Dual
    val daily = kind == GraphDetailKindUi.MuscleDailyVolume

    val filteredPrimary = resolvePrimarySeries(mode, range, since, now)
    val filteredSecondary = if (dual && !daily) {
        filterTrendPointsByRange(volume, since)
    } else {
        emptyList()
    }

    val primaryScale = primaryYScaleFor(kind, mode)
    val primaryColor = primaryColorRoleFor(kind, mode)
    // Lift e1RM series are kg in domain; convert for display when the primary scale is weight.
    val displayPrimary =
        if (primaryScale == DetailYScale.Weight) {
            filteredPrimary.scaleWeightFromKg(displayUnit)
        } else {
            filteredPrimary
        }
    // Daily volume: avg over training days only so 1W zero-fill matches longer sparse ranges.
    val statsDomain = if (daily) {
        graphDetailStatsNonZeroDays(displayPrimary)
    } else {
        graphDetailStats(displayPrimary)
    }
    val stats = statsDomain.toUi(
        scale = primaryScale,
        unitSuffix = unitSuffixFor(primaryScale, displayUnit),
        dailyVolume = daily,
        zone = zone,
    )

    return GraphDetailUiState.Ready(
        title = title,
        kind = kind,
        modes = modes,
        selectedMode = mode,
        selectedRange = range,
        ranges = availableRanges,
        stats = stats,
        primaryPoints = displayPrimary.toChartPoints(dailyVolume = daily, zone = zone),
        secondaryPoints = if (dual) {
            filteredSecondary.toChartPoints(dailyVolume = false, zone = zone)
        } else {
            null
        },
        primaryColorRole = primaryColor,
        secondaryColorRole = if (dual) GraphSeriesColorRole.Volume else null,
        primaryYScale = primaryScale,
        secondaryYScale = if (dual) DetailYScale.Volume else null,
    )
}

/** Converts weekly peak-e1RM trend values from kg analytics units into [displayUnit]. */
private fun List<TrendPoint>.scaleWeightFromKg(displayUnit: Weight.WeightUnit): List<TrendPoint> =
    map { point ->
        point.copy(
            value = WeightConversion.convertAmount(
                point.value,
                Weight.WeightUnit.Kg,
                displayUnit,
            ),
        )
    }

private fun GraphDetailSession.resolvePrimarySeries(
    mode: GraphViewMode,
    range: GraphTimeRangeUi,
    since: Instant?,
    now: Instant,
): List<TrendPoint> {
    val raw = when {
        kind == GraphDetailKindUi.MuscleDailyVolume -> volume
        mode == GraphViewMode.Volume -> volume
        else -> strengthOrProgress
    }
    val filtered = filterTrendPointsByRange(raw, since)
    // Rest-day bars for the 1W chip so the detail chart matches the board card.
    return if (kind == GraphDetailKindUi.MuscleDailyVolume && range == GraphTimeRangeUi.OneWeek) {
        zeroFillDailyTrendPoints(
            points = filtered,
            now = now,
            dayCount = MuscleDailyVolumeBuilder.RUNNING_VOLUME_DAY_COUNT,
            zone = zone,
        )
    } else {
        filtered
    }
}

private fun primaryYScaleFor(kind: GraphDetailKindUi, mode: GraphViewMode): DetailYScale = when {
    kind == GraphDetailKindUi.MuscleDailyVolume || mode == GraphViewMode.Volume -> DetailYScale.Volume
    kind == GraphDetailKindUi.Lift -> DetailYScale.Weight
    else -> DetailYScale.Progress
}

private fun primaryColorRoleFor(kind: GraphDetailKindUi, mode: GraphViewMode): GraphSeriesColorRole =
    when {
        kind == GraphDetailKindUi.MuscleDailyVolume -> GraphSeriesColorRole.DailyVolume
        mode == GraphViewMode.Volume -> GraphSeriesColorRole.Volume
        kind == GraphDetailKindUi.Lift -> GraphSeriesColorRole.Strength
        else -> GraphSeriesColorRole.Progress
    }

private fun unitSuffixFor(
    scale: DetailYScale,
    displayUnit: Weight.WeightUnit,
): String = when (scale) {
    DetailYScale.Weight -> displayUnit.shortLabel()
    DetailYScale.Volume -> "sets"
    DetailYScale.Progress -> ""
    DetailYScale.Steps -> "steps"
    // Graph detail never renders vitals (see primaryYScaleFor); no single unit across bpm/ms.
    DetailYScale.Vitals -> ""
}

private fun List<TrendPoint>.toChartPoints(
    dailyVolume: Boolean,
    zone: ZoneId,
): List<DetailChartPoint> =
    map {
        val epochDay = if (dailyVolume) {
            dayStartToEpochDay(it.weekStart.toEpochMilli(), zone)
        } else {
            weekStartToEpochDay(it.weekStart.toEpochMilli())
        }
        DetailChartPoint(
            xEpochDay = epochDay,
            value = it.value.toFloat(),
        )
    }

private fun GraphDetailStats.toUi(
    scale: DetailYScale,
    unitSuffix: String,
    dailyVolume: Boolean,
    zone: ZoneId,
): GraphStatsUi {
    val labelKey = when (scale) {
        DetailYScale.Weight -> GraphStatLabelKey.E1rm
        DetailYScale.Volume -> GraphStatLabelKey.Volume
        DetailYScale.Progress -> GraphStatLabelKey.Progress
        DetailYScale.Steps -> GraphStatLabelKey.Steps
        DetailYScale.Vitals -> GraphStatLabelKey.Vitals
    }
    val dateRangeText = if (dailyVolume) {
        formatDayStartRangeLabel(from, to, zone)
    } else {
        formatWeekStartRangeLabel(from, to)
    }
    return GraphStatsUi(
        averageText = average?.let { formatChartNumber(it, scale) } ?: "—",
        bestText = best?.let { formatChartNumber(it, scale) } ?: "—",
        averageLabelKey = labelKey,
        bestLabelKey = labelKey,
        dateRangeText = dateRangeText,
        unitSuffix = unitSuffix,
    )
}

internal fun GraphTimeRangeUi.toDomain(): GraphDetailTimeRange = when (this) {
    GraphTimeRangeUi.OneWeek -> GraphDetailTimeRange.OneWeek
    GraphTimeRangeUi.OneMonth -> GraphDetailTimeRange.OneMonth
    GraphTimeRangeUi.ThreeMonths -> GraphDetailTimeRange.ThreeMonths
    GraphTimeRangeUi.SixMonths -> GraphDetailTimeRange.SixMonths
    GraphTimeRangeUi.OneYear -> GraphDetailTimeRange.OneYear
    GraphTimeRangeUi.All -> GraphDetailTimeRange.All
}

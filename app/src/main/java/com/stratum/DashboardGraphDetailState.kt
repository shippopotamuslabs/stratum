package com.stratum

import com.stratum.domain.dashboard.GraphDetailKind
import com.stratum.domain.dashboard.GraphDetailSeries
import com.stratum.domain.dashboard.GraphDetailStats
import com.stratum.domain.dashboard.GraphDetailTimeRange
import com.stratum.domain.dashboard.TrendPoint
import com.stratum.domain.dashboard.filterTrendPointsByRange
import com.stratum.domain.dashboard.graphDetailStats
import com.stratum.domain.dashboard.windowStart
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Fullscreen graph detail presentation state — no domain types reach composables. */
sealed interface GraphDetailUiState {
    data object Hidden : GraphDetailUiState
    data object Loading : GraphDetailUiState
    data object Empty : GraphDetailUiState
    data class Ready(
        val title: String,
        val kind: GraphDetailKindUi,
        val metricTabs: List<GraphMetricTabUi>,
        val selectedMetric: GraphMetricTabUi,
        val selectedRange: GraphTimeRangeUi,
        val ranges: List<GraphTimeRangeUi>,
        val stats: GraphStatsUi,
        val primaryPoints: List<DetailPointUi>,
        val secondaryPoints: List<DetailPointUi>?,
        val primaryColorRole: GraphSeriesColorRole,
        val secondaryColorRole: GraphSeriesColorRole?,
        val primaryYScale: GraphYScaleUi,
        val secondaryYScale: GraphYScaleUi?,
    ) : GraphDetailUiState
}

enum class GraphDetailKindUi { Lift, Muscle }

/**
 * Metric tab for the detail chart. Labels are resolved from string resources in the UI layer.
 * [Both] plots primary + volume on dual Y axes.
 */
enum class GraphMetricTabUi {
    E1rm,
    Volume,
    Progress,
    Both,
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
}

/** Drives Y-axis tick formatting for [StratumDetailChart]. */
enum class GraphYScaleUi {
    Weight,
    Volume,
    Progress,
}

data class DetailPointUi(
    val weekStartEpochMillis: Long,
    val value: Float,
)

data class GraphStatsUi(
    val averageText: String,
    val bestText: String,
    val averageLabelKey: GraphStatLabelKey,
    val bestLabelKey: GraphStatLabelKey,
    val dateRangeText: String,
    val unitSuffix: String,
)

/** Which string resource pair to use for Avg / Best labels. */
enum class GraphStatLabelKey {
    E1rm,
    Volume,
    Progress,
}

/** Point-in-time snapshot used to recompute filtered UI without reloading domain. */
internal data class GraphDetailSession(
    val title: String,
    val kind: GraphDetailKindUi,
    val primary: List<TrendPoint>,
    val secondary: List<TrendPoint>,
    val selectedMetric: GraphMetricTabUi,
    val selectedRange: GraphTimeRangeUi,
)

internal fun GraphDetailSeries.toSession(): GraphDetailSession {
    val kindUi = when (kind) {
        GraphDetailKind.Lift -> GraphDetailKindUi.Lift
        GraphDetailKind.Muscle -> GraphDetailKindUi.Muscle
    }
    val defaultMetric = when (kindUi) {
        GraphDetailKindUi.Lift -> GraphMetricTabUi.E1rm
        GraphDetailKindUi.Muscle ->
            if (primary.isNotEmpty()) GraphMetricTabUi.Progress else GraphMetricTabUi.Volume
    }
    return GraphDetailSession(
        title = title,
        kind = kindUi,
        primary = primary,
        secondary = secondary,
        selectedMetric = defaultMetric,
        // Match dashboard board window (~13 weeks / DASHBOARD_PERFORMANCE_WINDOW_WEEKS).
        selectedRange = GraphTimeRangeUi.ThreeMonths,
    )
}

internal fun GraphDetailSession.withMetric(metric: GraphMetricTabUi): GraphDetailSession =
    copy(selectedMetric = metric)

internal fun GraphDetailSession.withRange(range: GraphTimeRangeUi): GraphDetailSession =
    copy(selectedRange = range)

internal fun GraphDetailSession.toReadyUi(now: Instant = Instant.now()): GraphDetailUiState.Ready {
    val metricTabs = metricTabsFor(kind)
    val selected = selectedMetric.takeIf { it in metricTabs } ?: metricTabs.first()
    val range = selectedRange
    val since = range.toDomain().windowStart(now)

    val showBoth = selected == GraphMetricTabUi.Both
    val statsMetric = statsMetricFor(selected, kind)
    val chartPrimaryMetric = chartPrimaryMetricFor(selected, kind)

    val filteredPrimary = filterTrendPointsByRange(seriesForMetric(chartPrimaryMetric), since)
    val filteredSecondary = if (showBoth) {
        filterTrendPointsByRange(secondary, since)
    } else {
        emptyList()
    }

    val statsDomain = graphDetailStats(filteredPrimary)
    val unitSuffix = unitSuffixFor(statsMetric)
    val stats = statsDomain.toUi(statsMetric, unitSuffix)

    return GraphDetailUiState.Ready(
        title = title,
        kind = kind,
        metricTabs = metricTabs,
        selectedMetric = selected,
        selectedRange = range,
        ranges = GraphTimeRangeUi.entries.toList(),
        stats = stats,
        primaryPoints = filteredPrimary.toDetailPoints(),
        secondaryPoints = if (showBoth) filteredSecondary.toDetailPoints() else null,
        primaryColorRole = colorRoleFor(chartPrimaryMetric),
        secondaryColorRole = if (showBoth) GraphSeriesColorRole.Volume else null,
        primaryYScale = yScaleFor(chartPrimaryMetric),
        secondaryYScale = if (showBoth) GraphYScaleUi.Volume else null,
    )
}

private fun metricTabsFor(kind: GraphDetailKindUi): List<GraphMetricTabUi> = when (kind) {
    GraphDetailKindUi.Lift -> listOf(
        GraphMetricTabUi.E1rm,
        GraphMetricTabUi.Volume,
        GraphMetricTabUi.Both,
    )
    GraphDetailKindUi.Muscle -> listOf(
        GraphMetricTabUi.Progress,
        GraphMetricTabUi.Volume,
        GraphMetricTabUi.Both,
    )
}

/** Series used for the main line (and stats) for the selected tab. */
private fun chartPrimaryMetricFor(
    selected: GraphMetricTabUi,
    kind: GraphDetailKindUi,
): GraphMetricTabUi = when (selected) {
    GraphMetricTabUi.Both -> when (kind) {
        GraphDetailKindUi.Lift -> GraphMetricTabUi.E1rm
        GraphDetailKindUi.Muscle -> GraphMetricTabUi.Progress
    }
    else -> selected
}

private fun statsMetricFor(
    selected: GraphMetricTabUi,
    kind: GraphDetailKindUi,
): GraphMetricTabUi = chartPrimaryMetricFor(selected, kind)

private fun GraphDetailSession.seriesForMetric(metric: GraphMetricTabUi): List<TrendPoint> =
    when (metric) {
        GraphMetricTabUi.E1rm, GraphMetricTabUi.Progress -> primary
        GraphMetricTabUi.Volume -> secondary
        GraphMetricTabUi.Both -> primary
    }

private fun colorRoleFor(metric: GraphMetricTabUi): GraphSeriesColorRole = when (metric) {
    GraphMetricTabUi.E1rm -> GraphSeriesColorRole.Strength
    GraphMetricTabUi.Progress -> GraphSeriesColorRole.Progress
    GraphMetricTabUi.Volume -> GraphSeriesColorRole.Volume
    GraphMetricTabUi.Both -> GraphSeriesColorRole.Strength
}

private fun yScaleFor(metric: GraphMetricTabUi): GraphYScaleUi = when (metric) {
    GraphMetricTabUi.E1rm -> GraphYScaleUi.Weight
    GraphMetricTabUi.Volume -> GraphYScaleUi.Volume
    GraphMetricTabUi.Progress -> GraphYScaleUi.Progress
    GraphMetricTabUi.Both -> GraphYScaleUi.Weight
}

private fun unitSuffixFor(metric: GraphMetricTabUi): String = when (metric) {
    GraphMetricTabUi.E1rm -> "kg"
    GraphMetricTabUi.Volume -> "sets"
    GraphMetricTabUi.Progress -> ""
    GraphMetricTabUi.Both -> "kg"
}

private fun List<TrendPoint>.toDetailPoints(): List<DetailPointUi> =
    map { DetailPointUi(weekStartEpochMillis = it.weekStart.toEpochMilli(), value = it.value.toFloat()) }

private fun GraphDetailStats.toUi(metric: GraphMetricTabUi, unitSuffix: String): GraphStatsUi {
    val labelKey = when (metric) {
        GraphMetricTabUi.E1rm, GraphMetricTabUi.Both -> GraphStatLabelKey.E1rm
        GraphMetricTabUi.Volume -> GraphStatLabelKey.Volume
        GraphMetricTabUi.Progress -> GraphStatLabelKey.Progress
    }
    return GraphStatsUi(
        averageText = average?.let { formatStatValue(it, metric) } ?: "—",
        bestText = best?.let { formatStatValue(it, metric) } ?: "—",
        averageLabelKey = labelKey,
        bestLabelKey = labelKey,
        dateRangeText = formatDateRange(from, to),
        unitSuffix = unitSuffix,
    )
}

internal fun formatStatValue(value: Double, metric: GraphMetricTabUi): String = when (metric) {
    GraphMetricTabUi.E1rm, GraphMetricTabUi.Both -> {
        if (value >= 100) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
    }
    GraphMetricTabUi.Volume -> {
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else String.format(Locale.US, "%.1f", value)
    }
    GraphMetricTabUi.Progress -> formatProgressValue(value)
}

/**
 * Formats tiny log-index progress values without a wall of trailing zeros or "-0".
 * Uses enough significant digits to distinguish nearby ticks.
 */
internal fun formatProgressValue(value: Double): String {
    if (abs(value) < PROGRESS_ZERO_EPSILON) return "0"
    val magnitude = abs(value)
    val formatted = when {
        magnitude >= PROGRESS_THRESHOLD_ONES -> String.format(Locale.US, "%.2f", value)
        magnitude >= PROGRESS_THRESHOLD_TENTHS -> String.format(Locale.US, "%.2f", value)
        magnitude >= PROGRESS_THRESHOLD_HUNDREDTHS -> String.format(Locale.US, "%.3f", value)
        magnitude >= PROGRESS_THRESHOLD_THOUSANDTHS -> String.format(Locale.US, "%.4f", value)
        else -> String.format(Locale.US, "%.2e", value)
    }
    return if (formatted.contains('e') || formatted.contains('E')) {
        formatted
    } else {
        formatted.trimEnd('0').trimEnd('.')
    }
}

private const val PROGRESS_ZERO_EPSILON = 1e-12
private const val PROGRESS_THRESHOLD_ONES = 1.0
private const val PROGRESS_THRESHOLD_TENTHS = 0.1
private const val PROGRESS_THRESHOLD_HUNDREDTHS = 0.01
private const val PROGRESS_THRESHOLD_THOUSANDTHS = 0.001

private fun formatDateRange(from: Instant?, to: Instant?): String {
    if (from == null || to == null) return ""
    val zone = ZoneId.systemDefault()
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
    val fromText = formatter.format(from.atZone(zone))
    val toText = formatter.format(to.atZone(zone))
    return if (fromText == toText) fromText else "$fromText – $toText"
}

internal fun GraphTimeRangeUi.toDomain(): GraphDetailTimeRange = when (this) {
    GraphTimeRangeUi.OneWeek -> GraphDetailTimeRange.OneWeek
    GraphTimeRangeUi.OneMonth -> GraphDetailTimeRange.OneMonth
    GraphTimeRangeUi.ThreeMonths -> GraphDetailTimeRange.ThreeMonths
    GraphTimeRangeUi.SixMonths -> GraphDetailTimeRange.SixMonths
    GraphTimeRangeUi.OneYear -> GraphDetailTimeRange.OneYear
    GraphTimeRangeUi.All -> GraphDetailTimeRange.All
}

package com.stratum

import com.stratum.core.ui.component.DetailChartPoint
import com.stratum.core.ui.component.DetailYScale
import com.stratum.core.ui.component.formatChartNumber
import com.stratum.core.ui.component.formatWeekStartRangeLabel
import com.stratum.core.ui.component.weekStartToEpochDay
import com.stratum.domain.dashboard.GraphDetailKind
import com.stratum.domain.dashboard.GraphDetailSeries
import com.stratum.domain.dashboard.GraphDetailStats
import com.stratum.domain.dashboard.GraphDetailTimeRange
import com.stratum.domain.dashboard.TrendPoint
import com.stratum.domain.dashboard.filterTrendPointsByRange
import com.stratum.domain.dashboard.graphDetailStats
import com.stratum.domain.dashboard.windowStart
import java.time.Instant

/** Fullscreen graph detail presentation state — no domain types reach composables. */
sealed interface GraphDetailUiState {
    data object Hidden : GraphDetailUiState
    data object Loading : GraphDetailUiState
    data object Empty : GraphDetailUiState
    data class Ready(
        val title: String,
        val kind: GraphDetailKindUi,
        /** Display modes for tabs (Primary | Volume | Dual). */
        val modes: List<GraphViewMode>,
        val selectedMode: GraphViewMode,
        val selectedRange: GraphTimeRangeUi,
        val ranges: List<GraphTimeRangeUi>,
        val stats: GraphStatsUi,
        /** Chart-ready points (UTC epoch day X) — no extra mapping in Compose. */
        val primaryPoints: List<DetailChartPoint>,
        val secondaryPoints: List<DetailChartPoint>?,
        val primaryColorRole: GraphSeriesColorRole,
        val secondaryColorRole: GraphSeriesColorRole?,
        val primaryYScale: DetailYScale,
        val secondaryYScale: DetailYScale?,
    ) : GraphDetailUiState
}

enum class GraphDetailKindUi { Lift, Muscle }

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
}

/** Full-history session; filter client-side without reloading. */
internal data class GraphDetailSession(
    val title: String,
    val kind: GraphDetailKindUi,
    val strengthOrProgress: List<TrendPoint>,
    val volume: List<TrendPoint>,
    val selectedMode: GraphViewMode,
    val selectedRange: GraphTimeRangeUi,
)

internal fun GraphDetailSeries.toSession(): GraphDetailSession {
    val kindUi = when (kind) {
        GraphDetailKind.Lift -> GraphDetailKindUi.Lift
        GraphDetailKind.Muscle -> GraphDetailKindUi.Muscle
    }
    val defaultMode = when (kindUi) {
        GraphDetailKindUi.Lift -> GraphViewMode.Primary
        GraphDetailKindUi.Muscle ->
            if (primary.isNotEmpty()) GraphViewMode.Primary else GraphViewMode.Volume
    }
    return GraphDetailSession(
        title = title,
        kind = kindUi,
        strengthOrProgress = primary,
        volume = secondary,
        selectedMode = defaultMode,
        selectedRange = GraphTimeRangeUi.ThreeMonths,
    )
}

internal fun modesFor(): List<GraphViewMode> =
    listOf(GraphViewMode.Primary, GraphViewMode.Volume, GraphViewMode.Dual)

internal fun GraphDetailSession.toReadyUi(now: Instant = Instant.now()): GraphDetailUiState.Ready {
    val modes = modesFor()
    val mode = selectedMode.takeIf { it in modes } ?: modes.first()
    val range = selectedRange.takeIf { it in GraphTimeRangeUi.entries } ?: GraphTimeRangeUi.ThreeMonths
    val since = range.toDomain().windowStart(now)

    val primarySeriesRaw = when (mode) {
        GraphViewMode.Primary, GraphViewMode.Dual -> strengthOrProgress
        GraphViewMode.Volume -> volume
    }
    val filteredPrimary = filterTrendPointsByRange(primarySeriesRaw, since)
    // Dual always keeps progress/strength as primary series — never silently swap to volume.
    val filteredSecondary = if (mode == GraphViewMode.Dual) {
        filterTrendPointsByRange(volume, since)
    } else {
        emptyList()
    }

    val primaryScale = when (mode) {
        GraphViewMode.Volume -> DetailYScale.Volume
        GraphViewMode.Primary, GraphViewMode.Dual -> when (kind) {
            GraphDetailKindUi.Lift -> DetailYScale.Weight
            GraphDetailKindUi.Muscle -> DetailYScale.Progress
        }
    }
    val secondaryScale = if (mode == GraphViewMode.Dual) DetailYScale.Volume else null

    val primaryColor = when (mode) {
        GraphViewMode.Volume -> GraphSeriesColorRole.Volume
        GraphViewMode.Primary, GraphViewMode.Dual -> when (kind) {
            GraphDetailKindUi.Lift -> GraphSeriesColorRole.Strength
            GraphDetailKindUi.Muscle -> GraphSeriesColorRole.Progress
        }
    }
    val secondaryColor = if (mode == GraphViewMode.Dual) GraphSeriesColorRole.Volume else null

    val statsDomain = graphDetailStats(filteredPrimary)
    val unitSuffix = when (primaryScale) {
        DetailYScale.Weight -> "kg"
        DetailYScale.Volume -> "sets"
        DetailYScale.Progress -> ""
    }
    val stats = statsDomain.toUi(primaryScale, unitSuffix)

    return GraphDetailUiState.Ready(
        title = title,
        kind = kind,
        modes = modes,
        selectedMode = mode,
        selectedRange = range,
        ranges = GraphTimeRangeUi.entries.toList(),
        stats = stats,
        primaryPoints = filteredPrimary.toChartPoints(),
        secondaryPoints = if (mode == GraphViewMode.Dual) {
            filteredSecondary.toChartPoints()
        } else {
            null
        },
        primaryColorRole = primaryColor,
        secondaryColorRole = secondaryColor,
        primaryYScale = primaryScale,
        secondaryYScale = secondaryScale,
    )
}

private fun List<TrendPoint>.toChartPoints(): List<DetailChartPoint> =
    map {
        DetailChartPoint(
            xEpochDay = weekStartToEpochDay(it.weekStart.toEpochMilli()),
            value = it.value.toFloat(),
        )
    }

private fun GraphDetailStats.toUi(scale: DetailYScale, unitSuffix: String): GraphStatsUi {
    val labelKey = when (scale) {
        DetailYScale.Weight -> GraphStatLabelKey.E1rm
        DetailYScale.Volume -> GraphStatLabelKey.Volume
        DetailYScale.Progress -> GraphStatLabelKey.Progress
    }
    return GraphStatsUi(
        averageText = average?.let { formatChartNumber(it, scale) } ?: "—",
        bestText = best?.let { formatChartNumber(it, scale) } ?: "—",
        averageLabelKey = labelKey,
        bestLabelKey = labelKey,
        dateRangeText = formatWeekStartRangeLabel(from, to),
        unitSuffix = unitSuffix,
    )
}

internal fun GraphTimeRangeUi.toDomain(): GraphDetailTimeRange = when (this) {
    GraphTimeRangeUi.OneMonth -> GraphDetailTimeRange.OneMonth
    GraphTimeRangeUi.ThreeMonths -> GraphDetailTimeRange.ThreeMonths
    GraphTimeRangeUi.SixMonths -> GraphDetailTimeRange.SixMonths
    GraphTimeRangeUi.OneYear -> GraphDetailTimeRange.OneYear
    GraphTimeRangeUi.All -> GraphDetailTimeRange.All
}

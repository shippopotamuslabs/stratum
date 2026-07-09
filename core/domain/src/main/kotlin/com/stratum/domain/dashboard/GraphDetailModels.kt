package com.stratum.domain.dashboard

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import java.time.Instant
import java.time.Period

/**
 * Subject for a fullscreen graph detail load — one lift or one muscle, full weekly history.
 */
sealed interface GraphDetailSubject {
    data class Lift(val liftId: LiftId) : GraphDetailSubject
    data class Muscle(val muscle: MuscleGroup) : GraphDetailSubject
}

/** Whether the detail graph is for a program lift or a muscle group. */
enum class GraphDetailKind {
    Lift,
    Muscle,
}

/**
 * Full-history dual series for one subject. [primary] is e1RM (lift) or progress index (muscle);
 * [secondary] is weekly hard-set volume. Values use the same units as the dashboard board tables
 * (e1RM is kg-normalized in the response pipeline).
 */
data class GraphDetailSeries(
    val title: String,
    val kind: GraphDetailKind,
    val primary: List<TrendPoint>,
    val secondary: List<TrendPoint>,
)

/**
 * Time window applied client-side after a full-history [GraphDetailSeries] load.
 *
 * Windows are week-oriented (data is one point per Monday UTC week start). There is no 1-week chip:
 * a 7-day window can include at most one bucket and is useless for trends.
 */
enum class GraphDetailTimeRange(
    val period: Period?,
) {
    /** ~4 week buckets — shortest useful line window. */
    OneMonth(Period.ofWeeks(4)),
    /** Matches [DASHBOARD_PERFORMANCE_WINDOW_WEEKS] board lookback. */
    ThreeMonths(Period.ofWeeks(DASHBOARD_PERFORMANCE_WINDOW_WEEKS)),
    SixMonths(Period.ofWeeks(26)),
    OneYear(Period.ofWeeks(52)),
    All(null),
}

/** Avg / peak over a filtered series. Null when [pointCount] is zero. */
data class GraphDetailStats(
    val average: Double?,
    val best: Double?,
    val pointCount: Int,
    val from: Instant?,
    val to: Instant?,
)

/**
 * Keeps points on or after [since] (inclusive). When [since] is null, returns the series sorted by
 * week start. Pure and testable.
 */
fun filterTrendPointsByRange(
    points: List<TrendPoint>,
    since: Instant?,
): List<TrendPoint> {
    val sorted = points.sortedBy { it.weekStart }
    if (since == null) return sorted
    return sorted.filter { !it.weekStart.isBefore(since) }
}

/** Lower bound instant for [range] relative to [now], or null for [GraphDetailTimeRange.All]. */
fun GraphDetailTimeRange.windowStart(now: Instant): Instant? =
    period?.let { now.minus(it) }

/**
 * Computes average, max (best/peak), and date span for a series. Empty input → zero counts and
 * null metrics.
 */
fun graphDetailStats(points: List<TrendPoint>): GraphDetailStats {
    if (points.isEmpty()) {
        return GraphDetailStats(
            average = null,
            best = null,
            pointCount = 0,
            from = null,
            to = null,
        )
    }
    val sorted = points.sortedBy { it.weekStart }
    return GraphDetailStats(
        average = sorted.map { it.value }.average(),
        best = sorted.maxOf { it.value },
        pointCount = sorted.size,
        from = sorted.first().weekStart,
        to = sorted.last().weekStart,
    )
}

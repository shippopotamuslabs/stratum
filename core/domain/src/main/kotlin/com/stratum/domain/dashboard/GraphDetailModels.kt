package com.stratum.domain.dashboard

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.DayBucketSupport
import java.time.Instant
import java.time.Period

/**
 * Subject for a fullscreen graph detail load — one lift, one muscle (weekly), or daily muscle volume.
 */
sealed interface GraphDetailSubject {
    data class Lift(val liftId: LiftId) : GraphDetailSubject
    data class Muscle(val muscle: MuscleGroup) : GraphDetailSubject
    /** Full daily hard-set history for one muscle (7-Day tab detail). */
    data class MuscleDailyVolume(val muscle: MuscleGroup) : GraphDetailSubject
}

/** Whether the detail graph is for a program lift, weekly muscle series, or daily muscle volume. */
enum class GraphDetailKind {
    Lift,
    Muscle,
    /** Daily hard-set volume only; [GraphDetailSeries.primary] is volume, [secondary] empty. */
    MuscleDailyVolume,
}

/**
 * Full-history dual series for one subject.
 * - Lift: [primary] e1RM, [secondary] weekly volume
 * - Muscle: [primary] progress index, [secondary] weekly volume
 * - MuscleDailyVolume: [primary] daily hard sets (UTC day start on [TrendPoint.weekStart]), [secondary] empty
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
 * Weekly series (lift / muscle) use week-oriented windows. [OneWeek] is offered for **daily**
 * volume series only (a single weekly bucket is useless at 7 days).
 */
enum class GraphDetailTimeRange(
    val period: Period?,
) {
    /** Last 7 calendar days — daily volume detail only. */
    OneWeek(Period.ofDays(7)),
    /** ~4 week buckets — shortest useful line window for weekly series. */
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

/** UTC calendar day at 00:00 for [instant]. Delegates to [DayBucketSupport]. */
fun utcDayStart(instant: Instant): Instant = DayBucketSupport.dayStartFor(instant)

/**
 * Ensures [dayCount] consecutive UTC days ending on [now]'s day (oldest first). Missing days get
 * value `0`. Used so 1W daily volume charts match the 7-Day board cards.
 */
fun zeroFillDailyTrendPoints(
    points: List<TrendPoint>,
    now: Instant,
    dayCount: Int,
): List<TrendPoint> =
    DayBucketSupport.zeroFillDaily(
        points = points.map { it.weekStart to it.value },
        asOf = now,
        dayCount = dayCount,
    ).map { (day, value) -> TrendPoint(weekStart = day, value = value) }

/**
 * Stats for daily volume charts: average/peak over **training days only** (value &gt; 0) so 1W
 * (zero-filled) and longer sparse ranges share the same metric. Empty / all-zero → empty stats.
 */
fun graphDetailStatsNonZeroDays(points: List<TrendPoint>): GraphDetailStats {
    val active = points.filter { it.value > 0.0 }
    return graphDetailStats(active)
}

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

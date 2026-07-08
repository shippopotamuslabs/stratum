package com.stratum.domain.dashboard

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.modeling.LiftWeeklyPerformance
import java.time.Instant

/**
 * Computes a dimensionless composite progress index for a single muscle across its contributing
 * lifts, over a bounded window.
 *
 * Method: for each contributing lift, normalize its weekly peak-e1RM series so the first in-window
 * week = 100 (so incomparable lifts — a 300lb squat vs a 90lb curl — share a common scale). Weight
 * each week's normalized value by that lift's share of the muscle's total hard-set volume, then
 * average across lifts per week. The result is a single [TrendPoint] series (weekStart, index)
 * tracking whether the muscle's strength is trending up or down — the "what is happening" line that
 * rides alongside the volume "why" line on the dashboard chart.
 *
 * This is the *naive* index for Phase 1. A future slice replaces it with the statistically
 * principled `MuscleWeeklyOutcomeIndex` from the dose-response pipeline once that runs as a worker;
 * the chart UI is data-source-agnostic so the swap is clean.
 */
object MuscleProgressIndex {
    /** Minimum lifts contributing volume to the muscle before a progress line is meaningful. */
    const val MIN_CONTRIBUTING_LIFTS = 2

    /** Minimum weekly points after which a direction can be called. */
    const val MIN_WEEKLY_POINTS = 3

    /**
     * @param muscle the target muscle (reserved for future per-muscle weighting policy)
     * @param weeklyPerformance all in-window per-lift/week performance rows for lifts that hit
     *   [muscle] (caller pre-filters)
     * @param liftVolumeShare each contributing lift's fractional share of the muscle's total
     *   in-window hard-set volume (sums to ~1.0)
     * @return the composite index series (weekStart, index where 100 = first-week baseline), or
     *   `null` when there are too few contributing lifts or too few shared weeks.
     */
    @Suppress("UnusedParameter") // muscle reserved for future per-muscle weighting
    fun compute(
        muscle: MuscleGroup,
        weeklyPerformance: List<LiftWeeklyPerformance>,
        liftVolumeShare: Map<LiftId, Double>,
    ): List<TrendPoint>? {
        if (liftVolumeShare.size < MIN_CONTRIBUTING_LIFTS) return null

        // Normalize each lift's series to its own first in-window week (=100), dropping lifts with
        // no usable baseline (empty series or non-positive first peak).
        val normalizedByLift = liftVolumeShare.keys.mapNotNull { liftId ->
            val liftSeries = weeklyPerformance.filter { it.liftId == liftId }.sortedBy { it.weekStart }
            val baseline = liftSeries.firstOrNull()?.peakE1rm?.takeIf { it > 0.0 } ?: return@mapNotNull null
            liftId to liftSeries.associate { it.weekStart to (it.peakE1rm / baseline * 100.0) }
        }.toMap()
        if (normalizedByLift.size < MIN_CONTRIBUTING_LIFTS) return null

        // Union of all week starts, sorted.
        val allWeeks = normalizedByLift.values.flatMap { it.keys }.distinct().sorted()
        if (allWeeks.size < MIN_WEEKLY_POINTS) return null

        // For each week, volume-weight the available normalized values and average. Renormalize by
        // the share actually present this week so a missing lift doesn't drag the index toward zero —
        // it just re-weights among the lifts that did train.
        return allWeeks.mapNotNull { week ->
            val presentShares = normalizedByLift.mapNotNull { (liftId, series) ->
                val index = series[week] ?: return@mapNotNull null
                val share = liftVolumeShare[liftId] ?: return@mapNotNull null
                index * share to share
            }
            if (presentShares.isEmpty()) return@mapNotNull null
            val weightedSum = presentShares.sumOf { it.first }
            val presentShareTotal = presentShares.sumOf { it.second }
            if (presentShareTotal <= 0.0) return@mapNotNull null
            TrendPoint(week, weightedSum / presentShareTotal)
        }.takeIf { it.size >= MIN_WEEKLY_POINTS }
    }
}

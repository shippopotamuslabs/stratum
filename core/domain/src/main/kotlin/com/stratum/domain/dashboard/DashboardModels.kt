package com.stratum.domain.dashboard

import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import java.time.Instant

/**
 * Top-level dashboard read model over a bounded window (default ~3 months). Answers
 * "Am I making progress? What is working, what is not, and why?" through two parallel lenses:
 *  - [muscles]: per-muscle weekly volume series (the "why": how much work) + a composite progress
 *    index (the "what": is strength trending up or down).
 *  - [lifts]: per-lift weekly peak-e1RM performance series + weekly hard-set volume.
 *
 * Both lists surface what is *not* working first (negative progress/volume slopes lead) so a glance
 * immediately lands on problems.
 *
 * Data source: the deterministic weekly tables (`muscle_weekly_stimulus`, `lift_weekly_performance`),
 * refreshed fire-and-forget on workout completion by
 * [com.stratum.domain.training.RefreshWeeklySeriesUseCase]. No recompute-on-load, no stale tables.
 */
data class DashboardSummary(
    val windowWeeks: Int,
    val asOf: Instant,
    val muscles: List<MuscleSeries>,
    val lifts: List<LiftSeries>,
)

/**
 * Per-muscle time series. [volumeSeries] is the weekly hard-set count (primary 1.0 / secondary 0.5
 * credit) — the "why". [progressSeries] is a dimensionless composite index: each contributing lift's
 * weekly peak-e1RM normalized to its own first-in-window week (=100), volume-weighted and averaged —
 * the "what". `null` when too few lifts/points exist to call a direction.
 */
data class MuscleSeries(
    val muscle: MuscleGroup,
    val displayName: String,
    val volumeSeries: List<TrendPoint>,
    val progressSeries: List<TrendPoint>?,
    val progressSlope: Double?,
    val meanWeeklyHardSets: Double,
    val contributingLiftCount: Int,
)

/**
 * Per-lift time series. [performanceSeries] is the weekly peak-e1RM (the strength signal);
 * [volumeSeries] is that lift's weekly hard-set count. [tenureWeeks] answers "how long have I been
 * on this lift?" — long tenure + stagnation is the classic swap-overdue signal.
 */
data class LiftSeries(
    val liftId: LiftId,
    val name: String,
    val performanceSeries: List<TrendPoint>,
    val volumeSeries: List<TrendPoint>,
    val latestE1rm: Weight?,
    val tenureWeeks: Int,
    val sessionCount: Int,
)

/** A single weekly point on a series. [value] is in the series's native units (sets, e1RM, or index). */
data class TrendPoint(
    val weekStart: Instant,
    val value: Double,
)

/**
 * Direction of a series, derived from its OLS slope. Used by the UI for badge coloring + sorting;
 * the chart itself shows the raw shape.
 */
enum class SeriesDirection {
    Up,
    Flat,
    Down,
    Insufficient,
}

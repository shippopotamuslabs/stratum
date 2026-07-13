package com.stratum.domain.dashboard

import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import java.time.Instant

/**
 * Top-level dashboard read model over a bounded window (default ~3 months). Answers
 * "Am I making progress? What is working, what is not, and why?" through two parallel lenses:
 *  - [muscles]: per-muscle weekly volume series (the "why": how much work) + outcome-index progress
 *    series (the "what": is strength trending up or down on the log-e1RM index).
 *  - [lifts]: per-lift weekly peak-e1RM performance series + weekly hard-set volume.
 *
 * Muscles are ordered by mean weekly hard-sets descending. Lifts are sectioned Upper then Lower
 * and ordered AxialCompound → NonAxialCompound → Isolation within each section.
 *
 * Data sources (kept fresh by workers / finalize):
 * - `muscle_weekly_stimulus`, `lift_weekly_performance` — cheap weekly refresh on workout complete
 * - `muscle_weekly_outcome_indices` — full dose-response extract (WorkManager, 28-day cadence)
 */
data class DashboardSummary(
    val windowWeeks: Int,
    val asOf: Instant,
    val muscles: List<MuscleSeries>,
    val lifts: List<LiftSeries>,
    /**
     * Per-muscle hard-set volume for the running 7-day window (UTC days), zero-filled. Empty when
     * no hard sets were logged in that window.
     */
    val runningDailyVolume: List<MuscleDailyVolumeSeries> = emptyList(),
    /** High-confidence progressing / regressing highlights — empty when nothing is clear. */
    val callouts: List<DashboardCallout> = emptyList(),
)

/**
 * Board series for the 7-Day volume tab. [dailyVolume] is always 7 points (oldest → newest), with
 * rest days at `0`. [TrendPoint.weekStart] holds the **UTC day start** (not a Monday week start).
 */
data class MuscleDailyVolumeSeries(
    val muscle: MuscleGroup,
    val displayName: String,
    val dailyVolume: List<TrendPoint>,
    val totalHardSets: Double,
    val contributingLiftCount: Int,
)

/**
 * Per-muscle time series. [volumeSeries] is the weekly hard-set count (primary 1.0 / secondary 0.5
 * credit) — the "why". [progressSeries] is the statistically principled muscle outcome index
 * (`MuscleWeeklyOutcomeIndex.indexValue`: log-e1RM deviation from an 8-wk median baseline,
 * credit × reliability weighted) written by the dose-response pipeline — the "what". `null` when
 * the worker has not produced enough in-window index rows for that muscle.
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
 * on this lift?" (calendar weeks since first-ever set — all-time, not window-scoped).
 * [isLower] / [kinematicProfile] drive dashboard sectioning and sort order (Upper/Lower, then
 * AxialCompound → NonAxialCompound → Isolation).
 */
data class LiftSeries(
    val liftId: LiftId,
    val name: String,
    val performanceSeries: List<TrendPoint>,
    val volumeSeries: List<TrendPoint>,
    val latestE1rm: Weight?,
    val tenureWeeks: Int,
    val sessionCount: Int,
    val isLower: Boolean,
    val kinematicProfile: KinematicProfile,
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

/**
 * Default performance / dashboard lookback window (calendar weeks). Shared by
 * [LoadDashboardUseCase] and check-in evaluation so both read the same series span.
 */
const val DASHBOARD_PERFORMANCE_WINDOW_WEEKS = 13

/**
 * Dead-band for muscle progress slope on the log-deviation index scale (≈ ln(e1RM) per week).
 * Matches [com.stratum.domain.response.modeling] progress threshold used for [VolumePosture].
 * Weekly deltas on this scale are typically ~0.001–0.01, not the kg/week scale used for lifts.
 */
const val MUSCLE_PROGRESS_SLOPE_THRESHOLD = 0.002

/**
 * Dead-band for lift peak-e1RM slope in kg (or lb) per week. Tuned for raw strength units.
 */
const val LIFT_E1RM_SLOPE_THRESHOLD = 0.5

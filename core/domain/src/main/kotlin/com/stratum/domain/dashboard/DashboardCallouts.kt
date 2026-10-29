package com.stratum.domain.dashboard

import kotlin.math.abs
import com.stratum.domain.lift.Lift

/**
 * A high-signal dashboard highlight: only surfaces when the trend is strong *and* the fit is clean
 * enough that a glance callout is safer than reading every card.
 */
data class DashboardCallout(
    val subjectName: String,
    val direction: CalloutDirection,
    val kind: CalloutKind,
    /** Ranking score — higher is clearer / stronger; not shown in UI. */
    val score: Double,
)

enum class CalloutDirection {
    Progressing,
    Regressing,
}

enum class CalloutKind {
    Muscle,
    Lift,
}

/**
 * Stronger slope dead-band than [MUSCLE_PROGRESS_SLOPE_THRESHOLD] so mild index drift does not
 * become a board-level callout.
 */
const val MUSCLE_CALLOUT_SLOPE_THRESHOLD = MUSCLE_PROGRESS_SLOPE_THRESHOLD * 2.0

/** Lift e1RM slope (kg/week) must clear this for a callout — above the chart badge dead-band. */
const val LIFT_CALLOUT_SLOPE_THRESHOLD = 1.0

/** Minimum R² so noisy series with a slight tilt stay silent. */
const val CALLOUT_MIN_R_SQUARED = 0.45

/**
 * Single-lift muscle indices are noisier; require a cleaner fit than [CALLOUT_MIN_R_SQUARED].
 */
const val CALLOUT_SINGLE_LIFT_MIN_R_SQUARED = 0.6

/** Minimum weekly points in the progress/performance series. */
const val CALLOUT_MIN_POINTS = 6

/** Minimum in-window sessions for a lift callout. */
const val LIFT_CALLOUT_MIN_SESSIONS = 6

/** Cap on how many callouts we surface at once — empty is fine. */
const val CALLOUT_MAX_COUNT = 3

/** Soft-caps for score factors (dashboard window is ~13 weeks). */
private const val SCORE_POINT_CAP_WEEKS = 13.0
private const val SCORE_LIFT_CAP = 4.0
private const val SCORE_SESSION_CAP = 20.0
private const val SCORE_FACTOR_FLOOR = 0.5
private const val SCORE_FACTOR_RANGE = 0.5
private const val MIN_MULTI_LIFT_COUNT = 2

/**
 * Picks high-confidence progressing / regressing callouts from muscle and lift series.
 * Prefer showing nothing over a weak signal. Within the cap: regressing first (actionable), then
 * progressing, ranked by [DashboardCallout.score].
 */
fun selectDashboardCallouts(
    muscles: List<MuscleSeries>,
    lifts: List<LiftSeries>,
    maxCount: Int = CALLOUT_MAX_COUNT,
): List<DashboardCallout> {
    if (maxCount <= 0) return emptyList()
    val candidates = muscles.mapNotNull { it.toMuscleCallout() } + lifts.mapNotNull { it.toLiftCallout() }
    if (candidates.isEmpty()) return emptyList()

    val regressing = candidates
        .filter { it.direction == CalloutDirection.Regressing }
        .sortedByDescending { it.score }
    val progressing = candidates
        .filter { it.direction == CalloutDirection.Progressing }
        .sortedByDescending { it.score }

    val selected = mutableListOf<DashboardCallout>()
    // Lead with the strongest regressing signal when present.
    regressing.firstOrNull()?.let { selected += it }
    progressing.firstOrNull()?.let { candidate ->
        if (!selected.containsSubject(candidate)) {
            selected += candidate
        }
    }
    // Fill remaining slots by overall score (either direction).
    val remainder = (regressing.drop(1) + progressing.drop(1))
        .sortedByDescending { it.score }
        .filterNot { selected.containsSubject(it) }
    selected += remainder.take((maxCount - selected.size).coerceAtLeast(0))
    return selected.take(maxCount)
}

private fun List<DashboardCallout>.containsSubject(other: DashboardCallout): Boolean =
    any { it.subjectName == other.subjectName && it.kind == other.kind }

private fun MuscleSeries.toMuscleCallout(): DashboardCallout? {
    val series = progressSeries
    val fit = series?.let(::olsFit)
    return when {
        series == null || fit == null -> null
        series.size < CALLOUT_MIN_POINTS -> null
        abs(fit.slope) < MUSCLE_CALLOUT_SLOPE_THRESHOLD -> null
        fit.rSquared < CALLOUT_MIN_R_SQUARED -> null
        // Single-lift index is noisier — require a cleaner fit.
        contributingLiftCount < MIN_MULTI_LIFT_COUNT &&
            fit.rSquared < CALLOUT_SINGLE_LIFT_MIN_R_SQUARED -> null
        else -> DashboardCallout(
            subjectName = displayName,
            direction = directionForSlope(fit.slope),
            kind = CalloutKind.Muscle,
            score = calloutScore(
                slope = fit.slope,
                rSquared = fit.rSquared,
                pointCount = series.size,
                secondaryCount = contributingLiftCount,
                secondaryCap = SCORE_LIFT_CAP,
            ),
        )
    }
}

private fun LiftSeries.toLiftCallout(): DashboardCallout? {
    val fit = olsFit(performanceSeries)
    return when {
        sessionCount < LIFT_CALLOUT_MIN_SESSIONS -> null
        performanceSeries.size < CALLOUT_MIN_POINTS -> null
        fit == null -> null
        abs(fit.slope) < LIFT_CALLOUT_SLOPE_THRESHOLD -> null
        fit.rSquared < CALLOUT_MIN_R_SQUARED -> null
        else -> DashboardCallout(
            subjectName = name,
            direction = directionForSlope(fit.slope),
            kind = CalloutKind.Lift,
            score = calloutScore(
                slope = fit.slope,
                rSquared = fit.rSquared,
                pointCount = performanceSeries.size,
                secondaryCount = sessionCount,
                secondaryCap = SCORE_SESSION_CAP,
            ),
        )
    }
}

private fun directionForSlope(slope: Double): CalloutDirection =
    if (slope > 0) CalloutDirection.Progressing else CalloutDirection.Regressing

private fun calloutScore(
    slope: Double,
    rSquared: Double,
    pointCount: Int,
    secondaryCount: Int,
    secondaryCap: Double,
): Double {
    val pointFactor = (pointCount.toDouble() / SCORE_POINT_CAP_WEEKS).coerceAtMost(1.0)
    val secondaryFactor = (secondaryCount.toDouble() / secondaryCap).coerceAtMost(1.0)
    val blendedSecondary = SCORE_FACTOR_FLOOR + SCORE_FACTOR_RANGE * secondaryFactor
    return abs(slope) * rSquared * pointFactor * blendedSecondary
}

package com.stratum.domain.dashboard

import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.CleanTrainingSet
import com.stratum.domain.response.MuscleVolumeCredit
import com.stratum.domain.response.WeeklySignalSupport
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Duration
import java.time.Instant

/**
 * Detail read model for one muscle on the two-pane Dashboard (STR-331): which lifts feed the
 * muscle and how hard, plus the most recent sessions that trained it.
 *
 * Built over [MUSCLE_DETAIL_WINDOW_WEEKS] calendar weeks from cleaned working sets, crediting
 * each lift with the same primary / secondary rule as the weekly stimulus table, so the lift
 * rows add up to the muscle's mean weekly hard sets over the trained weeks.
 */
data class MuscleDetail(
    val muscle: MuscleGroup,
    val lifts: List<MuscleDetailLift>,
    val recentSessions: List<MuscleDetailSession>,
)

/**
 * One lift's contribution to the muscle. [setsPerWeek] is credited hard sets averaged over the
 * weeks the muscle was trained in the window. [direction] is the slope of the lift's weekly peak
 * e1RM (kg) against [LIFT_E1RM_SLOPE_THRESHOLD].
 */
data class MuscleDetailLift(
    val liftId: LiftId,
    val name: String,
    val isPrimary: Boolean,
    val setsPerWeek: Double,
    val direction: SeriesDirection,
)

/**
 * A recent session that trained the muscle, with the top set of each contributing lift.
 * [workoutLabel] is null until the caller resolves the session's workout name.
 */
data class MuscleDetailSession(
    val sessionId: TrainingSessionId,
    val completedAt: Instant,
    val topSets: List<MuscleDetailTopSet>,
    val workoutLabel: String? = null,
)

/** Heaviest-e1RM working set of one lift within a session. */
data class MuscleDetailTopSet(
    val liftName: String,
    val weight: Weight,
    val reps: Int,
    val rir: Int?,
)

/** Muscle detail lookback, in calendar weeks including the current one. */
const val MUSCLE_DETAIL_WINDOW_WEEKS = 8L

/** How many recent sessions the muscle detail pane lists. */
const val MUSCLE_DETAIL_RECENT_SESSIONS = 4

/**
 * Builds [MuscleDetail] for [muscle] from [cleanSets]. Pure: callers load sets and catalog
 * [lifts], pass [now], and resolve session workout labels afterwards.
 */
fun buildMuscleDetail(
    muscle: MuscleGroup,
    cleanSets: List<CleanTrainingSet>,
    lifts: List<Lift>,
    now: Instant,
): MuscleDetail {
    val windowStart = WeeklySignalSupport.weekStartFor(now)
        .minus(Duration.ofDays(DAYS_PER_WEEK * (MUSCLE_DETAIL_WINDOW_WEEKS - 1)))
    val liftsById = lifts.associateBy { it.id }
    val credits = lifts.associate { lift -> lift.id to muscleCredit(lift, muscle) }
    val working = cleanSets.filter { row ->
        val liftId = row.sourceSet.liftId
        row.isWorkingSet &&
            liftId != null &&
            (credits[liftId] ?: 0.0) > 0.0 &&
            !row.sourceSet.completedAt.isBefore(windowStart) &&
            !row.sourceSet.completedAt.isAfter(now)
    }
    if (working.isEmpty()) {
        return MuscleDetail(muscle = muscle, lifts = emptyList(), recentSessions = emptyList())
    }
    val trainedWeeks = working
        .map { WeeklySignalSupport.weekStartFor(it.sourceSet.completedAt) }
        .distinct()
        .size
    val liftRows = working
        .groupBy { it.sourceSet.liftId!! }
        .mapNotNull { (liftId, rows) ->
            val lift = liftsById[liftId] ?: return@mapNotNull null
            val credit = credits.getValue(liftId)
            MuscleDetailLift(
                liftId = liftId,
                name = lift.name,
                isPrimary = credit >= MuscleVolumeCredit.PRIMARY_SET_CREDIT,
                setsPerWeek = rows.size * credit / trainedWeeks,
                direction = slopeToDirection(
                    olsSlope(weeklyPeakE1rm(rows)),
                    LIFT_E1RM_SLOPE_THRESHOLD,
                ),
            )
        }
        .sortedWith(compareByDescending<MuscleDetailLift> { it.setsPerWeek }.thenBy { it.name })
    val sessions = working
        .groupBy { it.sourceSet.sessionId }
        .map { (sessionId, rows) ->
            MuscleDetailSession(
                sessionId = sessionId,
                completedAt = rows.maxOf { it.sourceSet.completedAt },
                topSets = topSetsByLift(rows, liftsById),
            )
        }
        .sortedByDescending { it.completedAt }
        .take(MUSCLE_DETAIL_RECENT_SESSIONS)
    return MuscleDetail(muscle = muscle, lifts = liftRows, recentSessions = sessions)
}

/** Credit [lift] gives [muscle] per set: primary, secondary, or 0 when it does not train it. */
private fun muscleCredit(lift: Lift, muscle: MuscleGroup): Double {
    val weekCredits = mutableMapOf<MuscleGroup, MutableMap<Instant, Double>>()
    MuscleVolumeCredit.accumulateSetCredits(lift, Instant.EPOCH, weekCredits)
    return weekCredits[muscle]?.values?.sum() ?: 0.0
}

private fun weeklyPeakE1rm(rows: List<CleanTrainingSet>): List<TrendPoint> =
    rows.groupBy { WeeklySignalSupport.weekStartFor(it.sourceSet.completedAt) }
        .map { (week, weekRows) -> TrendPoint(week, weekRows.maxOf { it.estimatedOneRm }) }
        .sortedBy { it.weekStart }

private fun topSetsByLift(
    rows: List<CleanTrainingSet>,
    liftsById: Map<LiftId, Lift>,
): List<MuscleDetailTopSet> =
    rows.groupBy { it.sourceSet.liftId!! }
        .map { (liftId, liftRows) ->
            val top = liftRows.maxWith(
                compareBy<CleanTrainingSet> { it.estimatedOneRm }.thenBy { it.sourceSet.reps },
            )
            top to (liftsById[liftId]?.name ?: top.sourceSet.liftContext.liftName)
        }
        .sortedByDescending { (top, _) -> top.estimatedOneRm }
        .map { (top, name) ->
            MuscleDetailTopSet(
                liftName = name,
                weight = top.sourceSet.weight,
                reps = top.sourceSet.reps,
                rir = top.sourceSet.rir,
            )
        }

private const val DAYS_PER_WEEK = 7L

package com.stratum.domain.workout

import com.stratum.domain.training.EstimatedOneRm
import com.stratum.domain.importing.FatigueDecay
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightConversion
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.workout.fatigue.FatigueModel
import javax.inject.Inject

/**
 * Overlay-only recommendation pipeline (D4). Computes the live
 * [LiveSetRecommendation] map for upcoming unlogged sets from the most recent
 * performed set per lift. Never writes back into the snapshot.
 *
 * **Inputs must be snapshot prescriptions**, not a prior live-merge: [RenderedSet.recommendedWeight]
 * should be program/snapshot baseline (callers strip overlay recommendations before merge,
 * or build rendered sets without live weights). Feeding live weights back re-triggers too-easy
 * bumps every recompute.
 *
 * **Single shared weight** per lift for all post-anchor remaining slots (straight sets): floor
 * math uses the **last** remaining slot's RIR goal; each slot only projects its own reps.
 * Mixed RIR on later slots therefore changes rep counts, not load.
 *
 * **Post-anchor only:** unlogged sets before the last performed set do not receive live recs
 * and are excluded from [remainingCount] (avoids over-decay if logging is out of order).
 */
class RecomputeRecommendationsUseCase @Inject constructor(
    private val recommendationEngine: RecommendationEngine,
    private val fatigueModel: FatigueModel,
) {
    operator fun invoke(
        rendered: RenderedWorkout,
        liftsById: Map<LiftId, Lift>,
        fatigueByLiftId: Map<LiftId, FatigueDecay> = emptyMap(),
    ): Map<SetSlotId, LiveSetRecommendation> =
        rendered.lifts
            .map { lift -> recommendationsForLift(lift, liftsById, fatigueByLiftId) }
            .fold(emptyMap()) { acc, part -> acc + part }

    private fun recommendationsForLift(
        lift: RenderedWorkoutLift,
        liftsById: Map<LiftId, Lift>,
        fatigueByLiftId: Map<LiftId, FatigueDecay>,
    ): Map<SetSlotId, LiveSetRecommendation> {
        val lastPerformedSet = lift.sets
            .filter { it.loggedValue != null }
            .maxByOrNull { it.position }
        val lastPerformed = lastPerformedSet?.loggedValue
        if (lastPerformedSet == null || lastPerformed == null) return emptyMap()

        val setsByPosition = lift.sets.sortedBy { it.position }
        val anchorOrdinal = setsByPosition.indexOfFirst { it.slotId == lastPerformedSet.slotId }
        // Only unlogged sets *after* the fatigue anchor participate in live recs.
        val postAnchorUpcoming = if (anchorOrdinal < 0) {
            emptyList()
        } else {
            setsByPosition.drop(anchorOrdinal + 1).filter { it.loggedValue == null }
        }
        if (postAnchorUpcoming.isEmpty()) return emptyMap()

        val remainingCount = postAnchorUpcoming.size
        val lastUpcoming = postAnchorUpcoming.last()
        val incrementTiers = liftsById[lift.liftId]?.incrementTiers ?: emptyList()
        val fatigue = fatigueByLiftId[lift.liftId] ?: FatigueDecay(null, null)

        // One weight decision for the whole remaining block (last slot RIR = most
        // demanding floor check when RIR goals differ).
        val sharedOutcome = recommendationEngine.recommendNextSet(
            lastPerformed = lastPerformed,
            completedSlot = toSlot(lastPerformedSet),
            targetSlot = toSlot(lastUpcoming),
            currentWeight = lastUpcoming.recommendedWeight,
            remainingCount = remainingCount,
            incrementTiers = incrementTiers,
            perLiftSlope = fatigue.slope,
            perLiftIntercept = fatigue.intercept,
            loadType = lift.loadType,
            completedSetPrescribedWeight = lastPerformedSet.recommendedWeight,
        )
        val sharedWeight = when (sharedOutcome) {
            is RecommendationOutcome.Hold -> sharedOutcome.weight
            is RecommendationOutcome.Reduce -> sharedOutcome.weight
            null -> null
        }
        if (sharedWeight == null || sharedWeight.amount <= 0.0) return emptyMap()

        return postAnchorUpcoming.associate { set ->
            val targetOrdinal = setsByPosition.indexOfFirst { it.slotId == set.slotId }
            val stepsAhead = (targetOrdinal - anchorOrdinal).coerceAtLeast(1)
            val upcomingSetIndex = (1 + stepsAhead).coerceAtLeast(1)
            val recommendedReps = projectNextSetReps(
                lastPerformed = lastPerformed,
                targetWeight = sharedWeight,
                upcomingSetIndex = upcomingSetIndex,
                repMax = set.repMax,
                rirGoal = set.rirGoal,
                perLiftSlope = fatigue.slope,
                perLiftIntercept = fatigue.intercept,
            )
            set.slotId to LiveSetRecommendation(
                weight = sharedWeight,
                reps = recommendedReps.coerceAtLeast(1),
            )
        }
    }

    /**
     * Honest capacity at the slot RIR: never raises to `repMin`. Caps at `repMax` when set.
     * Shared weight already targets last-set ≥ [BACKOFF_REP_FLOOR]; intermediate slots step down.
     */
    private fun projectNextSetReps(
        lastPerformed: LoggedSetValue?,
        targetWeight: Weight,
        upcomingSetIndex: Int,
        repMax: Int,
        rirGoal: Int,
        perLiftSlope: Double?,
        perLiftIntercept: Double?,
    ): Int {
        val lastLoadLb = lastPerformed?.let { WeightConversion.loadMagnitudeLb(it.weight) } ?: 0.0
        val targetLoadLb = WeightConversion.loadMagnitudeLb(targetWeight)
        if (lastPerformed == null || lastLoadLb <= 0.0 || targetLoadLb <= 0.0) {
            return 1
        }
        val effectiveRir = lastPerformed.rir ?: 0
        val baselineReps = lastPerformed.reps + effectiveRir
        if (baselineReps <= 0) return 1
        val baselineOneRm = EstimatedOneRm.calculate(
            lastLoadLb,
            baselineReps,
        )
        val rawCapacityReps = fatigueModel.expectedRepsForAddedSet(
            baselineOneRm = baselineOneRm,
            targetWeight = targetWeight,
            nextSetIndex = upcomingSetIndex,
            perLiftSlope = perLiftSlope,
            perLiftIntercept = perLiftIntercept,
        )
        val atTargetRir = rawCapacityReps - rirGoal
        val honest = atTargetRir.coerceAtLeast(1)
        return if (repMax > 0) {
            honest.coerceAtMost(repMax.coerceAtLeast(1))
        } else {
            honest
        }
    }

    private fun toSlot(rendered: RenderedSet): SetSlot =
        SetSlot(
            slotId = rendered.slotId,
            workoutLiftId = rendered.workoutLiftId,
            liftId = rendered.liftId,
            position = rendered.position,
            setType = rendered.setType,
            isTopSet = rendered.isTopSet,
            repMin = rendered.repMin,
            repMax = rendered.repMax,
            rirGoal = rendered.rirGoal,
            restSeconds = rendered.restSeconds,
        )
}

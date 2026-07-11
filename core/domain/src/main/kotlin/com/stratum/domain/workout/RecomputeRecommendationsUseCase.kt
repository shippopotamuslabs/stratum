package com.stratum.domain.workout

import com.stratum.domain.training.EstimatedOneRm
import com.stratum.domain.importing.FatigueDecay
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.workout.fatigue.FatigueModel
import javax.inject.Inject

/**
 * Overlay-only recommendation pipeline (D4). Computes the live
 * [LiveSetRecommendation] map for upcoming unlogged sets from the most recent
 * performed set per lift. Never writes back into the snapshot.
 *
 * Weight comes from [RecommendationEngine.recommendNextSet] (hold-vs-reduce) and is shared
 * across remaining sets (last-set targeting). Reps are projected independently for each
 * upcoming slot via [FatigueModel], stepping the set index forward from the most recent
 * performed set — e.g. after a top set at 165×14@2 RIR, remaining backoff slots at the
 * same load and 1 RIR get distinct, monotonically non-increasing rep targets (population
 * slope ≈ −2 capacity reps per set index). The [FatigueModel] is also consumed transitively
 * by [EstimateAddedSetRepsUseCase] in the domain layer; the live drop-set estimation path
 * can be wired when the UI gains drop-set entry.
 */
class RecomputeRecommendationsUseCase @Inject constructor(
    private val recommendationEngine: RecommendationEngine,
    private val fatigueModel: FatigueModel,
) {
    /**
     * Computes overlay-only recommendations for upcoming unlogged sets from the most
     * recent performed set per lift (D4). Never writes back into the snapshot.
     *
     * @param rendered the merged rendered workout (structure + snapshot + overlay merged).
     * @param liftsById catalog lookup used to resolve each lift's [Lift.incrementTiers].
     * @param fatigueByLiftId per-lift fatigue decay overrides from imported history; absent
     *   entries fall back to the population defaults inside [FatigueModel].
     * @return map of upcoming [SetSlotId] to its live recommendation; slots with no
     *   derivable recommendation (e.g. first set of the session — no e1RM yet) are absent.
     */
    operator fun invoke(
        rendered: RenderedWorkout,
        liftsById: Map<LiftId, Lift>,
        fatigueByLiftId: Map<LiftId, FatigueDecay> = emptyMap(),
    ): Map<SetSlotId, LiveSetRecommendation> {
        val recommendations = mutableMapOf<SetSlotId, LiveSetRecommendation>()
        for (lift in rendered.lifts) {
            // Most recent performed set (by workout position) is the fatigue/e1RM anchor.
            // maxBy position — not list order — so appended overlay slots and non-sorted
            // merges still pick the true last logged set.
            val lastPerformedSet = lift.sets
                .filter { it.loggedValue != null }
                .maxByOrNull { it.position }
            val lastPerformed = lastPerformedSet?.loggedValue
            if (lastPerformedSet != null && lastPerformed != null) {
                val completedSlot = toSlot(lastPerformedSet)
                // Ordinal order among currently rendered slots (position-sorted). DeleteSet
                // does not renumber positions, so raw position deltas would over-decay
                // across gaps; ordinal distance counts only sets still in the workout.
                val setsByPosition = lift.sets.sortedBy { it.position }
                val anchorOrdinal = setsByPosition.indexOfFirst { it.slotId == lastPerformedSet.slotId }
                val upcoming = lift.sets.filter { it.loggedValue == null }
                // One weight for all remaining sets (per design): remainingCount is the total
                // upcoming set count, used by the engine to target the last set.
                val remainingCount = upcoming.size
                val incrementTiers = liftsById[lift.liftId]?.incrementTiers ?: emptyList()
                val fatigue = fatigueByLiftId[lift.liftId] ?: FatigueDecay(null, null)
                // loadType drives the RecommendationEngine's bodyweight short-circuit and
                // assisted sign handling; defaults to Weighted when the catalog lacks the lift.
                val loadType = lift.loadType
                for (set in upcoming) {
                    val rec = recommendationEngine.recommendNextSet(
                        lastPerformed = lastPerformed,
                        completedSlot = completedSlot,
                        targetSlot = toSlot(set),
                        currentWeight = set.recommendedWeight,
                        remainingCount = remainingCount,
                        incrementTiers = incrementTiers,
                        perLiftSlope = fatigue.slope,
                        loadType = loadType,
                    )
                    val recommendedWeight = when (rec) {
                        is RecommendationOutcome.Hold -> rec.weight
                        is RecommendationOutcome.Reduce -> rec.weight
                        null -> null
                    }
                    // Relative set index from the performed anchor: the performed set is index 1
                    // (no extra decay), the next rendered slot is index 2, then 3, …
                    val targetOrdinal = setsByPosition.indexOfFirst { it.slotId == set.slotId }
                    val stepsAhead = if (anchorOrdinal >= 0 && targetOrdinal >= 0) {
                        targetOrdinal - anchorOrdinal
                    } else {
                        set.position - lastPerformedSet.position
                    }
                    val upcomingSetIndex = (1 + stepsAhead).coerceAtLeast(1)
                    val recommendedReps = projectNextSetReps(
                        lastPerformed = lastPerformed,
                        targetWeight = recommendedWeight ?: set.recommendedWeight,
                        upcomingSetIndex = upcomingSetIndex,
                        repMin = set.repMin,
                        repMax = set.repMax,
                        rirGoal = set.rirGoal,
                        perLiftSlope = fatigue.slope,
                        perLiftIntercept = fatigue.intercept,
                    )
                    if (recommendedWeight != null && recommendedReps > 0) {
                        recommendations[set.slotId] =
                            LiveSetRecommendation(recommendedWeight, recommendedReps)
                    }
                }
            }
        }
        return recommendations
    }

    /**
     * Projects expected reps for an upcoming set at the slot's RIR goal. Derives a
     * baseline e1RM from the most recent performed set (`weight` × `reps + rir`) and
     * applies the [FatigueModel] linear decay at [upcomingSetIndex] (1 = performed
     * anchor, 2 = next set, …). The fatigue model returns raw capacity reps at 0 RIR,
     * so the slot's [rirGoal] is subtracted to express the recommendation at the same
     * RIR intent as the target slot. Falls back to the target slot's `repMin` when no
     * performed set is available yet (first set of the session — no e1RM derivable).
     * Result is clamped to the slot's `repMin..repMax` so the placeholder never drifts
     * outside the prescribed rep range.
     */
    private fun projectNextSetReps(
        lastPerformed: LoggedSetValue?,
        targetWeight: Weight,
        upcomingSetIndex: Int,
        repMin: Int,
        repMax: Int,
        rirGoal: Int,
        perLiftSlope: Double?,
        perLiftIntercept: Double?,
    ): Int {
        if (lastPerformed == null || lastPerformed.weight.amount <= 0.0 || targetWeight.amount <= 0.0) {
            return repMin.coerceAtLeast(0)
        }
        val effectiveRir = lastPerformed.rir ?: 0
        val baselineReps = lastPerformed.reps + effectiveRir
        if (baselineReps <= 0) return repMin.coerceAtLeast(0)
        val baselineOneRm = EstimatedOneRm.calculate(
            lastPerformed.weight.amount,
            baselineReps,
        )
        val rawCapacityReps = fatigueModel.expectedRepsForAddedSet(
            baselineOneRm = baselineOneRm,
            targetWeight = targetWeight,
            nextSetIndex = upcomingSetIndex,
            perLiftSlope = perLiftSlope,
            perLiftIntercept = perLiftIntercept,
        )
        // Global recommended-rep floor: never surface fewer than BACKOFF_REP_FLOOR reps.
        // Weight reduction to make the floor achievable is owned by RecommendationEngine;
        // this clamp is the display safety net (incl. backoff slots with repMin=repMax=0).
        val floor = BACKOFF_REP_FLOOR
        val atTargetRir = (rawCapacityReps - rirGoal).coerceAtLeast(floor)
        return if (repMin > 0 && repMax > 0) {
            atTargetRir.coerceIn(maxOf(repMin, floor), maxOf(repMax, floor))
        } else {
            atTargetRir
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

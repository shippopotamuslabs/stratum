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
 * Weight comes from [RecommendationEngine.recommendNextSet] (hold-vs-reduce). Reps are
 * projected one set-index forward from the most recent performed set via [FatigueModel] —
 * e.g. a lifter who just hit 75 lb for 8 reps at 2 RIR can expect roughly one fewer rep at
 * the same load on the next set under the population decay. The [FatigueModel] is also
 * consumed transitively by [EstimateAddedSetRepsUseCase] in the domain layer; the live
 * drop-set estimation path can be wired when the UI gains drop-set entry.
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
            val lastPerformedSet = lift.sets.firstOrNull { it.loggedValue != null }
            val lastPerformed = lastPerformedSet?.loggedValue
            val completedSlot = lastPerformedSet?.let { toSlot(it) }
            val upcoming = lift.sets.filter { it.loggedValue == null }
            val performedCount = lift.sets.count { it.loggedValue != null }
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
                val recommendedReps = projectNextSetReps(
                    lastPerformed = lastPerformed,
                    targetWeight = recommendedWeight ?: set.recommendedWeight,
                    upcomingSetIndex = performedCount + 1,
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
        return recommendations
    }

    /**
     * Projects expected reps for the next upcoming set at the slot's RIR goal. Derives a
     * baseline e1RM from the most recent performed set (`weight` × `reps + rir`) and
     * applies the [FatigueModel] linear-decay one set-index forward. The fatigue model
     * returns raw capacity reps at 0 RIR, so the slot's [rirGoal] is subtracted to express
     * the recommendation at the same RIR intent as the target slot. Falls back to the
     * target slot's `repMin` when no performed set is available yet (first set of the
     * session — no e1RM derivable). Result is clamped to the slot's `repMin..repMax` so
     * the placeholder never drifts outside the prescribed rep range.
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
        val atTargetRir = (rawCapacityReps - rirGoal).coerceAtLeast(1)
        return if (repMin > 0 && repMax > 0) atTargetRir.coerceIn(repMin, repMax) else atTargetRir
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

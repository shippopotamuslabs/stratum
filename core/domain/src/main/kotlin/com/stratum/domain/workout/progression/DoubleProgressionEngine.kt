package com.stratum.domain.workout.progression

import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.training.EstimatedOneRm
import com.stratum.domain.workout.BACKOFF_REP_FLOOR
import com.stratum.domain.workout.SlotRecommendation
import com.stratum.domain.workout.fatigue.projectReps
import javax.inject.Inject

/**
 * Double-progression engine (D8 primary). Increments load when the TOP of the rep
 * range was achieved at the RIR target in history; holds load otherwise. Backoff
 * slots inherit the top-set weight (AMRAP-at-RIR). Pure.
 *
 * Increment unit default is 5 lb; production wiring can supply a per-lift override
 * through the eventual config (D8). History is read in historical-priority order
 * (n−1 first); the most-recent entry drives the increment decision.
 */
class DoubleProgressionEngine @Inject constructor() : ProgressionEngine {
    override val scheme: ProgressionScheme = ProgressionScheme.Double

    override fun recommend(request: SlotProgressionRequest): SlotRecommendation {
        val slot = request.slot
        val latest = request.history.firstOrNull()
        val prevWeight = latest?.loggedSet?.weight
        val prevReps = latest?.loggedSet?.reps

        // Bodyweight: no load lever — always recommend 0 weight, rep-only progression.
        if (request.loadType == LoadType.Bodyweight) {
            val zeroWeight = Weight(0.0, prevWeight?.unit ?: Weight.WeightUnit.Lb)
            val baselineReps = latest?.loggedSet?.reps ?: slot.repMin
            return SlotRecommendation(
                slotId = slot.slotId,
                recommendedWeight = zeroWeight,
                baselineExpectedReps = baselineReps,
                previousWeight = prevWeight,
                previousReps = prevReps,
            )
        }

        val baseWeight = latest?.loggedSet?.weight ?: request.fallbackOneRm ?: defaultWeight()
        val baselineExpectedReps = latest?.loggedSet?.reps ?: slot.repMin

        val targetWeight = request.currentTopSetWeight ?: run {
            if (!slot.isTopSet) {
                baseWeight
            } else {
                val incremented = shouldIncrement(latest, slot)
                val increment = if (incremented) incrementFor() else Weight(0.0, baseWeight.unit)
                Weight(baseWeight.amount + increment.amount, baseWeight.unit)
                    .capAssistedAtZero(request.loadType)
            }
        }

        val recommendedReps = if (prevWeight != null && prevWeight == targetWeight) {
            if (slot.isTopSet) {
                val incremented = shouldIncrement(latest, slot)
                if (incremented) {
                    slot.repMin
                } else {
                    val prevSet = latest.loggedSet
                    rirAdjustedReps(prevSet.reps, prevSet.rir, slot)
                        ?: prevSet.reps.coerceIn(slot.repMin, slot.repMax)
                }
            } else {
                val prevSet = latest.loggedSet
                rirAdjustedReps(
                    prevSet.reps,
                    prevSet.rir,
                    slot,
                ) ?: baselineExpectedReps
            }
        } else {
            val priorRec = request.priorSlotRecommendation
            val priorSlot = request.priorSlot
            if (priorRec != null && priorSlot != null) {
                val priorWeight = priorRec.recommendedWeight
                val priorReps = priorRec.baselineExpectedReps
                val priorRirGoal = priorSlot.rirGoal
                val priorE1rm = EstimatedOneRm.calculate(priorWeight.amount, priorReps + priorRirGoal)
                val rawCapacityReps = projectReps(
                    baselineOneRm = priorE1rm,
                    targetWeight = targetWeight,
                    nextSetIndex = slot.position + 1,
                    perLiftSlope = request.perLiftSlope,
                    perLiftIntercept = request.perLiftIntercept,
                )
                val repsAtTargetRir = rawCapacityReps - slot.rirGoal
                if (slot.isTopSet) {
                    repsAtTargetRir.coerceIn(slot.repMin, slot.repMax)
                } else {
                    repsAtTargetRir.coerceAtLeast(BACKOFF_REP_FLOOR)
                }
            } else {
                if (slot.isTopSet) {
                    slot.repMin
                } else {
                    val prevSet = latest?.loggedSet
                    rirAdjustedReps(
                        prevSet?.reps ?: slot.repMin,
                        prevSet?.rir,
                        slot,
                    ) ?: baselineExpectedReps
                }
            }
        }

        val isProjected = latest != null || (request.priorSlotRecommendation?.isProjected == true)

        return SlotRecommendation(
            slotId = slot.slotId,
            recommendedWeight = targetWeight,
            baselineExpectedReps = recommendedReps,
            previousWeight = prevWeight,
            previousReps = prevReps,
            isProjected = isProjected,
        )
    }

    private fun shouldIncrement(
        latest: com.stratum.domain.workout.SlotHistoryEntry?,
        slot: com.stratum.domain.workout.SetSlot,
    ): Boolean {
        val set = latest?.loggedSet ?: return false
        // TOP of range at RIR: reps >= repMax AND rir >= rirGoal.
        return set.reps >= slot.repMax && (set.rir != null && set.rir >= slot.rirGoal)
    }

    private fun incrementFor(): Weight =
        Weight(DEFAULT_DOUBLE_INCREMENT_LB, Weight.WeightUnit.Lb)

    private fun defaultWeight(): Weight =
        Weight(0.0, Weight.WeightUnit.Lb)

    private companion object {
        const val DEFAULT_DOUBLE_INCREMENT_LB = 5.0
    }
}

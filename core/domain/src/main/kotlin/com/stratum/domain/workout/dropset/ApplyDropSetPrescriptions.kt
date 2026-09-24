package com.stratum.domain.workout.dropset

import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.program.SetType
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SlotRecommendation

/**
 * Rewrites [DropSet] rows in a progression snapshot so they are **not** left at the
 * shared straight-set load. Double/Linear engines have no set-type awareness; the
 * snapshot orchestrator emits every slot at one shared weight, then this pass applies
 * the live drop formula (20% off prior + zero-rest rep projection).
 *
 * Processes drops in position order so unlogged chains re-anchor on the rewritten
 * prior drop. Preserves previous* / isProjected on each [SlotRecommendation].
 */
fun applyDropSetPrescriptions(
    slots: List<SetSlot>,
    recs: List<SlotRecommendation>,
    estimateDropSetWeight: EstimateDropSetWeightUseCase = EstimateDropSetWeightUseCase(),
    incrementTiers: List<IncrementTier> = emptyList(),
    loadType: LoadType = LoadType.Weighted,
    micro: List<Weight> = emptyList(),
): List<SlotRecommendation> {
    if (slots.size != recs.size || slots.isEmpty()) return recs
    val out = recs.toMutableList()
    for (i in 1 until slots.size) {
        if (slots[i].setType == SetType.DropSet) {
            val rewritten = prescribeDropFromPrior(
                dropSlot = slots[i],
                priorSlot = slots[i - 1],
                priorRec = out[i - 1],
                estimateDropSetWeight = estimateDropSetWeight,
                incrementTiers = incrementTiers,
                loadType = loadType,
                micro = micro,
            )
            if (rewritten != null) {
                out[i] = out[i].copy(
                    recommendedWeight = rewritten.weight,
                    baselineExpectedReps = rewritten.reps,
                )
            }
        }
    }
    return out
}

private data class DropPrescription(val weight: Weight, val reps: Int)

private fun prescribeDropFromPrior(
    dropSlot: SetSlot,
    priorSlot: SetSlot,
    priorRec: SlotRecommendation,
    estimateDropSetWeight: EstimateDropSetWeightUseCase,
    incrementTiers: List<IncrementTier>,
    loadType: LoadType,
    micro: List<Weight> = emptyList(),
): DropPrescription? {
    val priorWeight = priorRec.recommendedWeight
    if (priorWeight.amount <= 0.0) return null
    val priorReps = priorRec.baselineExpectedReps.coerceAtLeast(1)
    val priorRir = priorSlot.rirGoal.coerceAtLeast(0)
    val dropWeight = estimateDropSetWeight(
        precedingWeight = priorWeight,
        incrementTiers = incrementTiers,
        loadType = loadType,
        micro = micro,
        capacityAtRir = { amountLb ->
            DropSetRepProjection.expectedRepsAtRir0(
                priorWeight = priorWeight,
                priorReps = priorReps,
                priorRir = priorRir,
                dropWeight = Weight(amountLb, Weight.WeightUnit.Lb),
            )
        },
    )
    val dropReps = DropSetRepProjection.expectedReps(
        priorWeight = priorWeight,
        priorReps = priorReps,
        priorRir = priorRir,
        dropWeight = dropWeight,
        rirGoal = dropSlot.rirGoal.coerceAtLeast(0),
    )
    return DropPrescription(weight = dropWeight, reps = dropReps.coerceAtLeast(1))
}

package com.stratum.domain.workout.progression

import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.incrementFor
import com.stratum.domain.lift.roundToIncrement
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.training.EstimatedOneRm
import com.stratum.domain.workout.BACKOFF_REP_FLOOR
import com.stratum.domain.workout.SlotHistoryEntry
import com.stratum.domain.workout.SlotRecommendation
import com.stratum.domain.workout.fatigue.projectReps
import javax.inject.Inject

/**
 * Double-progression engine (D8 primary). Increments load when the TOP of the rep
 * range was achieved at the RIR target in history; holds load otherwise. Backoff
 * slots inherit the top-set weight (AMRAP-at-RIR). Pure.
 *
 * Increment unit default is 5 lb; production wiring can supply a per-lift override
 * through the eventual config (D8). Math baseline comes from
 * [SlotProgressionRequest.progressionBaseline] — the orchestrator builds a
 * capacity-ordered recent-session block per lift, so recommendations are monotonically
 * non-increasing across slots even when prior-set RIR was misreported ("reps going up"
 * history). `history` is used only for `previousWeight`/`previousReps` (UI display).
 */
class DoubleProgressionEngine @Inject constructor() : ProgressionEngine {
    override val scheme: ProgressionScheme = ProgressionScheme.Double

    override fun recommend(request: SlotProgressionRequest): SlotRecommendation {
        val slot = request.slot
        val latest = request.history.firstOrNull()
        val prevWeight = latest?.loggedSet?.weight
        val prevReps = latest?.loggedSet?.reps
        val baselineEntry = baselineEntryFor(request.progressionBaseline)

        // Bodyweight: no load lever — always recommend 0 weight, rep-only progression.
        if (request.loadType == LoadType.Bodyweight) {
            val zeroWeight = Weight(0.0, prevWeight?.unit ?: Weight.WeightUnit.Lb)
            val baselineReps = baselineEntry?.loggedSet?.reps ?: slot.repMin
            return SlotRecommendation(
                slotId = slot.slotId,
                recommendedWeight = zeroWeight,
                baselineExpectedReps = baselineReps,
                previousWeight = prevWeight,
                previousReps = prevReps,
            )
        }

        val baseWeight = baselineEntry?.loggedSet?.weight ?: request.fallbackOneRm ?: defaultWeight()
        val baselineExpectedReps = baselineEntry?.loggedSet?.reps ?: slot.repMin

        val targetWeight = request.currentTopSetWeight ?: run {
            if (!slot.isTopSet) {
                baseWeight
            } else {
                val incremented = shouldIncrement(baselineEntry, slot)
                val increment =
                    if (incremented) {
                        incrementFor(request, baseWeight)
                    } else {
                        Weight(0.0, baseWeight.unit)
                    }
                val raw = Weight(baseWeight.amount + increment.amount, baseWeight.unit)
                request.incrementTiers
                    .roundToIncrement(raw, request.loadType)
                    .capAssistedAtZero(request.loadType)
            }
        }

        val recommendedReps = when {
            // No recent baseline for this slot — project from the prior slot rec via
            // the fatigue model (e.g. slots beyond the most-recent session's set count).
            baselineEntry == null && request.progressionBaseline is ProgressionBaseline.ProjectFromPrior ->
                projectFromPrior(request, slot, targetWeight)
                    ?: coldStartReps(slot, baselineExpectedReps)

            baselineEntry != null && baselineEntry.loggedSet.weight == targetWeight -> {
                if (slot.isTopSet) {
                    val incremented = shouldIncrement(baselineEntry, slot)
                    if (incremented) {
                        slot.repMin
                    } else {
                        val prevSet = baselineEntry.loggedSet
                        rirAdjustedReps(prevSet.reps, prevSet.rir, slot)
                            ?: prevSet.reps.coerceIn(slot.repMin, slot.repMax)
                    }
                } else {
                    val prevSet = baselineEntry.loggedSet
                    rirAdjustedReps(
                        prevSet.reps,
                        prevSet.rir,
                        slot,
                    ) ?: baselineExpectedReps
                }
            }

            else -> {
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
                    coldStartReps(slot, baselineExpectedReps)
                }
            }
        }

        val isProjected = baselineEntry != null || (request.priorSlotRecommendation?.isProjected == true)

        return SlotRecommendation(
            slotId = slot.slotId,
            recommendedWeight = targetWeight,
            baselineExpectedReps = recommendedReps,
            previousWeight = prevWeight,
            previousReps = prevReps,
            isProjected = isProjected,
        )
    }

    private fun projectFromPrior(
        request: SlotProgressionRequest,
        slot: com.stratum.domain.workout.SetSlot,
        targetWeight: Weight,
    ): Int? {
        val priorRec = request.priorSlotRecommendation ?: return null
        val priorSlot = request.priorSlot ?: return null
        val priorE1rm = EstimatedOneRm.calculate(
            priorRec.recommendedWeight.amount,
            priorRec.baselineExpectedReps + priorSlot.rirGoal,
        )
        val rawCapacityReps = projectReps(
            baselineOneRm = priorE1rm,
            targetWeight = targetWeight,
            nextSetIndex = slot.position + 1,
            perLiftSlope = request.perLiftSlope,
            perLiftIntercept = request.perLiftIntercept,
        )
        val repsAtTargetRir = rawCapacityReps - slot.rirGoal
        return if (slot.isTopSet) {
            repsAtTargetRir.coerceIn(slot.repMin, slot.repMax)
        } else {
            repsAtTargetRir.coerceAtLeast(BACKOFF_REP_FLOOR)
        }
    }

    private fun coldStartReps(
        slot: com.stratum.domain.workout.SetSlot,
        baselineExpectedReps: Int,
    ): Int = if (slot.isTopSet) slot.repMin else baselineExpectedReps

    private fun baselineEntryFor(baseline: ProgressionBaseline?): SlotHistoryEntry? =
        when (baseline) {
            is ProgressionBaseline.FromRecent -> baseline.entry
            ProgressionBaseline.ProjectFromPrior, null -> null
        }

    private fun shouldIncrement(
        latest: SlotHistoryEntry?,
        slot: com.stratum.domain.workout.SetSlot,
    ): Boolean {
        val set = latest?.loggedSet ?: return false
        // TOP of range at RIR: reps >= repMax AND rir >= rirGoal.
        return set.reps >= slot.repMax && (set.rir != null && set.rir >= slot.rirGoal)
    }

    private fun incrementFor(request: SlotProgressionRequest, baseWeight: Weight): Weight {
        val fromTiers = request.incrementTiers.incrementFor(baseWeight)
        if (fromTiers != null) return fromTiers
        return Weight(DEFAULT_DOUBLE_INCREMENT_LB, baseWeight.unit)
    }

    private fun defaultWeight(): Weight =
        Weight(0.0, Weight.WeightUnit.Lb)

    private companion object {
        const val DEFAULT_DOUBLE_INCREMENT_LB = 5.0
    }
}

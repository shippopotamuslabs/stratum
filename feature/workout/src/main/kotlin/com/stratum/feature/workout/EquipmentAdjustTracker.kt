package com.stratum.feature.workout

import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Weight
import com.stratum.domain.program.identity.WorkoutLiftId

/**
 * Session-scoped bookkeeping for the live "Adjust Equipment" chip.
 *
 * Owns its collections: cards join [consumed] via [dismiss] (X affordance) or when
 * their effective tiers or adder stops change after first-show (a real tier or
 * micro save resolves the chip; untouched editor visits leave both identical so
 * the chip stays). [update] folds save-detection into the raw signal and returns
 * the visible ids; departed cards are pruned. Cleared on session start via [clear].
 */
internal class EquipmentAdjustTracker {
    private val consumed: MutableSet<WorkoutLiftId> = mutableSetOf()
    private val seenSteps: MutableMap<WorkoutLiftId, StepFingerprint> = mutableMapOf()

    fun dismiss(workoutLiftId: WorkoutLiftId) {
        consumed.add(workoutLiftId)
        seenSteps.remove(workoutLiftId)
    }

    fun clear() {
        consumed.clear()
        seenSteps.clear()
    }

    fun update(
        rawIds: Set<WorkoutLiftId>,
        tiersByWorkoutLiftId: Map<WorkoutLiftId, List<IncrementTier>>,
        presentIds: Set<WorkoutLiftId>,
        microByWorkoutLiftId: Map<WorkoutLiftId, List<Weight>> = emptyMap(),
    ): Set<WorkoutLiftId> {
        seenSteps.keys.retainAll(presentIds)
        consumed.retainAll(presentIds)
        for (id in rawIds) {
            if (id in consumed) continue
            val current =
                StepFingerprint(
                    tiers = tiersByWorkoutLiftId[id].orEmpty(),
                    micro = microByWorkoutLiftId[id].orEmpty(),
                )
            val seen = seenSteps[id]
            if (seen == null) {
                seenSteps[id] = current
            } else if (seen != current) {
                consumed.add(id)
                seenSteps.remove(id)
            }
        }
        return rawIds - consumed
    }

    private data class StepFingerprint(
        val tiers: List<IncrementTier>,
        val micro: List<Weight>,
    )
}

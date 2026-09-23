package com.stratum.feature.workout

import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.program.identity.WorkoutLiftId

/**
 * Session-scoped bookkeeping for the live "Adjust Equipment" chip.
 *
 * Owns its collections: cards join [consumed] via [dismiss] (X affordance) or when
 * their effective tiers change after first-show (a real tier save resolves the chip;
 * untouched editor visits leave tiers identical so the chip stays). [update] folds
 * save-detection into the raw signal and returns the visible ids; departed cards are
 * pruned. Cleared on session start via [clear].
 */
internal class EquipmentAdjustTracker {
    private val consumed: MutableSet<WorkoutLiftId> = mutableSetOf()
    private val seenTiers: MutableMap<WorkoutLiftId, List<IncrementTier>> = mutableMapOf()

    fun dismiss(workoutLiftId: WorkoutLiftId) {
        consumed.add(workoutLiftId)
        seenTiers.remove(workoutLiftId)
    }

    fun clear() {
        consumed.clear()
        seenTiers.clear()
    }

    fun update(
        rawIds: Set<WorkoutLiftId>,
        tiersByWorkoutLiftId: Map<WorkoutLiftId, List<IncrementTier>>,
        presentIds: Set<WorkoutLiftId>,
    ): Set<WorkoutLiftId> {
        seenTiers.keys.retainAll(presentIds)
        consumed.retainAll(presentIds)
        for (id in rawIds) {
            if (id in consumed) continue
            val current = tiersByWorkoutLiftId[id].orEmpty()
            val seen = seenTiers[id]
            if (seen == null) {
                seenTiers[id] = current
            } else if (seen != current) {
                consumed.add(id)
                seenTiers.remove(id)
            }
        }
        return rawIds - consumed
    }
}

package com.stratum.feature.workout

import com.stratum.domain.lift.EffectiveSteps
import com.stratum.domain.program.identity.WorkoutLiftId

/**
 * Session-scoped bookkeeping for the live "Adjust Equipment" chip.
 *
 * Owns its collections: cards join [consumed] via [dismiss] (X affordance) or when
 * their effective steps change after first-show (a real tier or micro save resolves
 * the chip; untouched editor visits leave both identical so the chip stays).
 * [update] folds save-detection into the raw signal and returns the visible ids;
 * departed cards are pruned. Cleared on session start via [clear].
 *
 * Steps arrive as one [EffectiveSteps] value per lift so tiers and micro cannot
 * drift out of sync across parallel maps; comparison is data-class equality.
 */
internal class EquipmentAdjustTracker {
    private val consumed: MutableSet<WorkoutLiftId> = mutableSetOf()
    private val seenSteps: MutableMap<WorkoutLiftId, EffectiveSteps> = mutableMapOf()

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
        stepsByWorkoutLiftId: Map<WorkoutLiftId, EffectiveSteps>,
        presentIds: Set<WorkoutLiftId>,
    ): Set<WorkoutLiftId> {
        seenSteps.keys.retainAll(presentIds)
        consumed.retainAll(presentIds)
        for (id in rawIds) {
            // Unknown steps stay visible: never compare against empty and resolve.
            val current = stepsByWorkoutLiftId[id]
            if (id in consumed || current == null) continue
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
}

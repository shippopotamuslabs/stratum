package com.stratum.domain.program.generation.selection

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup

/**
 * Tracks lift usage across a program cycle so selection can prefer unused exercises
 * for the same movement pattern before repeating a proven favorite.
 */
data class LiftSelectionState(
    val cycleUsedLiftIdsByPattern: MutableMap<MovementPattern, MutableSet<String>> = mutableMapOf(),
    val sessionPrimaryLiftByMusclePattern: MutableMap<String, String> = mutableMapOf(),
) {
    fun musclePatternKey(pattern: MovementPattern, primaryMuscle: MuscleGroup): String = "$pattern:$primaryMuscle"

    fun recordSelection(pattern: MovementPattern, primaryMuscle: MuscleGroup, liftId: String, isVariantAlt: Boolean) {
        cycleUsedLiftIdsByPattern.getOrPut(pattern) { mutableSetOf() }.add(liftId)
        if (!isVariantAlt) {
            sessionPrimaryLiftByMusclePattern[musclePatternKey(pattern, primaryMuscle)] = liftId
        }
    }

    fun avoidLiftIdsForSlot(pattern: MovementPattern, primaryMuscle: MuscleGroup, slotId: String): Set<String> {
        if (!slotId.endsWith("-alt")) return emptySet()
        val primary = sessionPrimaryLiftByMusclePattern[musclePatternKey(pattern, primaryMuscle)]
        return if (primary != null) setOf(primary) else emptySet()
    }
}

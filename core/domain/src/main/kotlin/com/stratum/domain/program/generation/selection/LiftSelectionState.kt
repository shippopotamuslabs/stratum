package com.stratum.domain.program.generation.selection

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup

/**
 * Tracks lift usage across a program cycle so selection can prefer unused exercises
 * for the same movement pattern before repeating a proven favorite.
 *
 * [cycleUsedLiftIds] is the hard global pool: once a lift is chosen anywhere in the
 * program, later slots drop it unless it is the last remaining candidate.
 */
data class LiftSelectionState(
    val cycleUsedLiftIdsByPattern: MutableMap<MovementPattern, MutableSet<String>> = mutableMapOf(),
    val cycleUsedLiftIds: MutableSet<String> = mutableSetOf(),
    val sessionPrimaryLiftByMusclePattern: MutableMap<String, String> = mutableMapOf(),
) {
    fun musclePatternKey(pattern: MovementPattern, primaryMuscle: MuscleGroup): String = "$pattern:$primaryMuscle"

    fun recordSelection(pattern: MovementPattern, primaryMuscle: MuscleGroup, liftId: String, isVariantAlt: Boolean) {
        cycleUsedLiftIdsByPattern.getOrPut(pattern) { mutableSetOf() }.add(liftId)
        cycleUsedLiftIds.add(liftId)
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

package com.stratum.domain.program.generation.selection

/**
 * Tracks lift usage across a program cycle so selection can prefer unused exercises
 * for the same movement pattern before repeating a proven favorite.
 */
data class LiftSelectionState(
    val cycleUsedLiftIdsByPattern: MutableMap<String, MutableSet<String>> = mutableMapOf(),
    val sessionPrimaryLiftByMusclePattern: MutableMap<String, String> = mutableMapOf(),
) {
    fun musclePatternKey(pattern: String, primaryMuscle: String): String = "$pattern:$primaryMuscle"

    fun recordSelection(pattern: String, primaryMuscle: String, liftId: String, isVariantAlt: Boolean) {
        cycleUsedLiftIdsByPattern.getOrPut(pattern) { mutableSetOf() }.add(liftId)
        if (!isVariantAlt) {
            sessionPrimaryLiftByMusclePattern[musclePatternKey(pattern, primaryMuscle)] = liftId
        }
    }

    fun avoidLiftIdsForSlot(pattern: String, primaryMuscle: String, slotId: String): Set<String> {
        if (!slotId.endsWith("-alt")) return emptySet()
        val primary = sessionPrimaryLiftByMusclePattern[musclePatternKey(pattern, primaryMuscle)]
        return if (primary != null) setOf(primary) else emptySet()
    }
}

@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements")

package com.stratum.domain.program.generation.slots

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.program.generation.context.ProgramGenerationContext

/**
 * When a specialized muscle has only one in-domain slot, inject a second slot so
 * CP-SAT can spread direct volume across two exercises instead of stacking 6–8
 * sets on one lift. Only added when the user has at least two enabled lifts for
 * the pattern (otherwise the session would duplicate the same exercise).
 */
internal fun injectSpecializedVariantSlots(
    slots: MutableList<WorkoutSlotTemplate>,
    context: ProgramGenerationContext,
) {
    val insertions = mutableListOf<Pair<Int, WorkoutSlotTemplate>>()
    for ((muscle, level) in context.specialization) {
        if (level != SpecializationLevel.Specialize) continue
        val variantInsertion = variantInsertionForMuscle(slots, muscle, context) ?: continue
        insertions.add(variantInsertion)
    }
    insertions.sortByDescending { it.first }
    for ((index, variant) in insertions) {
        slots.add(index + 1, variant)
    }
}

private fun variantInsertionForMuscle(
    slots: List<WorkoutSlotTemplate>,
    muscle: MuscleGroup,
    context: ProgramGenerationContext,
): Pair<Int, WorkoutSlotTemplate>? {
    val inDomain = slots.filter { it.primaryMuscle == muscle && !it.isCrossDomainTouch }
    if (inDomain.size != 1) return null
    val primary = inDomain.single()
    if (primary.role != SlotRole.Isolation && primary.role != SlotRole.Anchor) return null
    if (!hasMultipleLiftOptions(primary.pattern, muscle, context)) return null
    val variantId = "${primary.slotId}-alt"
    if (slots.any { it.slotId == variantId }) return null
    val primaryIndex = slots.indexOfFirst { it.slotId == primary.slotId }
    if (primaryIndex < 0) return null
    return primaryIndex to primary.copy(
        slotId = variantId,
        lengthBiasPreference = alternateLengthBias(primary.lengthBiasPreference),
        siblingVariant = SiblingVariant.B,
    )
}

internal fun hasMultipleLiftOptions(
    pattern: MovementPattern,
    muscle: MuscleGroup,
    context: ProgramGenerationContext,
): Boolean {
    var count = 0
    for (lift in context.liftsById.values) {
        if (lift.id !in context.enabledLiftIds) continue
        if (lift.movementPattern != pattern && pattern !in lift.movementPatterns) continue
        if (muscle !in lift.primaryMuscles) continue
        count++
        if (count >= 2) return true
    }
    return false
}

private fun alternateLengthBias(
    current: MuscleLengthBiasPreference?,
): MuscleLengthBiasPreference =
    when (current) {
        null,
        MuscleLengthBiasPreference.Any,
        MuscleLengthBiasPreference.MidRange,
        -> MuscleLengthBiasPreference.Lengthened
        MuscleLengthBiasPreference.Lengthened -> MuscleLengthBiasPreference.Shortened
        MuscleLengthBiasPreference.Shortened -> MuscleLengthBiasPreference.Lengthened
    }

@file:Suppress("MagicNumber", "LoopWithTooManyJumpStatements")

package com.stratum.domain.program.generation.slots

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.volume.TrainingDomain
import com.stratum.domain.program.generation.volume.muscleTrainingDomain
import com.stratum.domain.program.generation.volume.availablePatternsByMuscle

/**
 * Builds an upper session skewed toward specialized muscles (manual "Upper B" style):
 * direct iso for each specialized upper muscle plus variant slots when lifts allow.
 */
internal fun applySpecFocusedUpperSlots(
    slots: MutableList<WorkoutSlotTemplate>,
    context: ProgramGenerationContext,
) {
    for ((muscle, level) in context.specialization) {
        if (level != SpecializationLevel.Specialize) continue
        if (muscleTrainingDomain(muscle.name) != TrainingDomain.Upper) continue
        ensureDirectIsolationSlot(slots, muscle, context)
    }
    for (slot in slots.indices) {
        val muscle = slots[slot].primaryMuscle
        if (context.specialization[muscle] == SpecializationLevel.Specialize) {
            if (slots[slot].role == SlotRole.Optional) {
                slots[slot] = slots[slot].copy(role = SlotRole.Isolation)
            }
        }
    }
    injectSpecializedVariantSlots(slots, context)
}

private fun ensureDirectIsolationSlot(
    slots: MutableList<WorkoutSlotTemplate>,
    muscle: MuscleGroup,
    context: ProgramGenerationContext,
) {
    if (slots.any { it.primaryMuscle == muscle && !it.isCrossDomainTouch }) return
    val pattern =
        preferredIsolationPattern(muscle.name, context) ?: return
    val slotId = "spec-focus-${muscle.name.lowercase()}"
    if (slots.any { it.slotId == slotId }) return
    slots.add(
        WorkoutSlotTemplate(
            slotId = slotId,
            role = SlotRole.Isolation,
            pattern = runCatching { MovementPattern.valueOf(pattern) }.getOrNull() ?: return,
            primaryMuscle = muscle,
            repProfile = RpRepProfile.Isolation,
        ),
    )
}

private fun preferredIsolationPattern(
    muscle: String,
    context: ProgramGenerationContext,
): String? {
    val available = availablePatternsByMuscle(context)[muscle].orEmpty()
    val preferredOrder =
        when (muscle) {
            "Bicep" -> listOf("ElbowFlexion")
            "Tricep" -> listOf("ElbowExtension")
            "LateralDeltoid" -> listOf("ShoulderAbduction")
            "PosteriorDeltoid" -> listOf("ShoulderHorizontalAbduction")
            "AnteriorDeltoid" -> listOf("VerticalPress")
            "Chest" -> listOf("HorizontalPress", "InclinePress")
            "Back" -> listOf("HorizontalPull", "VerticalPull")
            else -> emptyList()
        }
    return preferredOrder.firstOrNull { pattern ->
        pattern in available && hasPrimaryLiftForPattern(muscle, pattern, context)
    } ?: available.firstOrNull { pattern -> hasPrimaryLiftForPattern(muscle, pattern, context) }
}

private fun hasPrimaryLiftForPattern(
    muscle: String,
    pattern: String,
    context: ProgramGenerationContext,
): Boolean {
    val muscleEnum = runCatching { MuscleGroup.valueOf(muscle) }.getOrNull() ?: return false
    return context.liftsById.values.any { lift ->
        lift.id in context.enabledLiftIds &&
            lift.movementPattern?.name == pattern &&
            muscleEnum in lift.primaryMuscles
    }
}

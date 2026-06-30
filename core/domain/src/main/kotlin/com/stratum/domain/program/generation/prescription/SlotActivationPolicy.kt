package com.stratum.domain.program.generation.prescription

import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.split.SlotRole
import com.stratum.domain.program.generation.split.WorkoutSlotTemplate

/**
 * CP-SAT may activate or deactivate optional and variant slots instead of
 * filtering them out before the solve.
 */
fun isSolverActivatableSlot(slot: FilledSlot): Boolean =
    slot.role == SlotRole.Optional || slot.slotId.endsWith("-alt")

fun prefersSlotActivation(slot: FilledSlot, context: ProgramGenerationContext): Boolean =
    when {
        slot.role == SlotRole.Optional ->
            prefersOptionalSlotActivation(slot.toWorkoutSlotTemplate(), context)
        slot.slotId.endsWith("-alt") ->
            (context.specialization[slot.primaryMuscle] ?: "Maintenance") == "Specialize"
        else -> false
    }

internal fun prefersOptionalSlotActivation(
    slot: WorkoutSlotTemplate,
    context: ProgramGenerationContext,
): Boolean {
    if ((context.specialization[slot.primaryMuscle] ?: "Maintenance") == "Specialize") {
        return true
    }
    if (context.sessionLengthMinutes == null) return true
    return context.sessionLengthMinutes >= OPTIONAL_SLOT_MIN_SESSION_MINUTES
}

private const val OPTIONAL_SLOT_MIN_SESSION_MINUTES = 60

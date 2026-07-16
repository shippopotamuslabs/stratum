@file:Suppress("LoopWithTooManyJumpStatements")

package com.stratum.domain.program.generation.prescription

import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.generation.context.PRIMARY_SET_CREDIT
import com.stratum.domain.program.generation.reference.PATTERN_SECONDARY_MUSCLE_CREDITS
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.slots.MuscleLengthBiasPreference
import com.stratum.domain.program.generation.slots.SessionTypeId
import com.stratum.domain.program.generation.slots.SiblingVariant
import com.stratum.domain.program.generation.slots.SlotRole
import com.stratum.domain.program.generation.slots.WorkoutSlotTemplate

data class FilledSlot(
    val slotId: String,
    val role: SlotRole,
    val pattern: MovementPattern,
    val primaryMuscle: MuscleGroup,
    val repProfile: RpRepProfile,
    val lengthBiasPreference: MuscleLengthBiasPreference? = null,
    val siblingVariant: SiblingVariant? = null,
    val isCrossDomainTouch: Boolean = false,
    val allowedFatigueProfiles: Set<FatigueProfile>? = null,
    var setCount: Int,
) {
    fun toWorkoutSlotTemplate(): WorkoutSlotTemplate =
        WorkoutSlotTemplate(
            slotId = slotId,
            role = role,
            pattern = pattern,
            primaryMuscle = primaryMuscle,
            repProfile = repProfile,
            lengthBiasPreference = lengthBiasPreference,
            siblingVariant = siblingVariant,
            isCrossDomainTouch = isCrossDomainTouch,
            allowedFatigueProfiles = allowedFatigueProfiles,
        )
}

data class SessionSlotPlan(
    val sessionType: SessionTypeId,
    val name: String,
    val slots: List<WorkoutSlotTemplate>,
    val siblingIndex: Int,
    val position: Int,
    val filledSlots: MutableList<FilledSlot>,
)

internal fun muscleCreditsInSession(plan: SessionSlotPlan, muscle: MuscleGroup): Double {
    var total = 0.0
    for (slot in plan.filledSlots) {
        if (slot.setCount <= 0) continue
        if (slot.primaryMuscle == muscle) {
            total += slot.setCount * PRIMARY_SET_CREDIT
            continue
        }
        for (secondary in PATTERN_SECONDARY_MUSCLE_CREDITS[slot.pattern].orEmpty()) {
            if (secondary.muscle == muscle) {
                total += slot.setCount * secondary.creditPerSet
            }
        }
    }
    return total
}

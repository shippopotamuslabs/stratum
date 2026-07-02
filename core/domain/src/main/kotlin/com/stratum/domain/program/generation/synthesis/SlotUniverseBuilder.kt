@file:Suppress("MagicNumber")

package com.stratum.domain.program.generation.synthesis

import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.split.SessionComposition
import com.stratum.domain.program.generation.split.SessionTypeId
import com.stratum.domain.program.generation.split.SlotRole
import com.stratum.domain.program.generation.split.WorkoutSlotTemplate
import com.stratum.domain.program.generation.split.slotsForSessionType
import com.stratum.domain.program.generation.volume.TrainingDomain
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets
import com.stratum.domain.program.generation.volume.muscleTrainingDomain

private const val MAX_UNIVERSE_SLOTS = 80

private val LOWER_PRIMARY_PATTERNS =
    setOf(
        "SquatPattern",
        "HipHinge",
        "KneeExtension",
        "KneeFlexion",
        "PlantarFlexion",
    )

private val SOURCE_SESSION_TYPES =
    listOf(
        SessionTypeId.Upper,
        SessionTypeId.Lower,
        SessionTypeId.FullBody,
        SessionTypeId.UpperPush,
        SessionTypeId.UpperPull,
    )

fun buildSlotUniverse(
    context: ProgramGenerationContext,
    weeklyTargets: WeeklyVolumeTargets,
): List<SynthesisSlot> {
    val priorityMuscles =
        context.specialization
            .filterValues { level -> level == "Specialize" || level == "MinimumVolume" }
            .keys
    val relevantMuscles =
        weeklyTargets
            .filter { (_, target) -> target.aim > 0 }
            .keys
            .plus(priorityMuscles)
            .toSet()

    val raw = linkedMapOf<String, WorkoutSlotTemplate>()
    for (sessionType in SOURCE_SESSION_TYPES) {
        val slots =
            slotsForSessionType(
                sessionType = sessionType,
                context = context,
                siblingIndex = 0,
                sessionPosition = 0,
                composition =
                    when (sessionType) {
                        SessionTypeId.Upper -> SessionComposition.UpperBalanced
                        SessionTypeId.Lower -> SessionComposition.LowerPrimary
                        else -> null
                    },
            )
        for (slot in slots) {
            if (!isSlotRelevant(slot, relevantMuscles, context)) continue
            val key = slotKey(slot)
            if (key !in raw) {
                raw[key] = slot
            }
        }
    }

    return raw.values
        .map { slot -> slot.toSynthesisSlot() }
        .take(MAX_UNIVERSE_SLOTS)
}

private fun isSlotRelevant(
    slot: WorkoutSlotTemplate,
    relevantMuscles: Set<String>,
    context: ProgramGenerationContext,
): Boolean {
    // G14: Exclude the direct VerticalPress/AnteriorDeltoid slot when anterior is not Specialize.
    // Anterior PV is reachable via HorizontalPress/InclinePress secondaries (0.5/set); a dedicated
    // overhead secondary churns session time the maintenance prescription should spend on arms/lateral.
    if (slot.pattern == "VerticalPress" && slot.primaryMuscle == "AnteriorDeltoid" &&
        (context.specialization["AnteriorDeltoid"] ?: "Maintenance") != "Specialize"
    ) {
        return false
    }
    if (slot.primaryMuscle in relevantMuscles) return true
    if ((context.specialization[slot.primaryMuscle] ?: "Maintenance") == "Specialize") return true
    if (slot.role == SlotRole.Anchor) return true
    return false
}

private fun slotKey(slot: WorkoutSlotTemplate): String =
    listOf(
        slot.pattern,
        slot.primaryMuscle,
        slot.role.name,
        slot.isCrossDomainTouch.toString(),
        slot.siblingVariant?.name ?: "Any",
    ).joinToString("|")

private fun WorkoutSlotTemplate.toSynthesisSlot(): SynthesisSlot =
    SynthesisSlot(
        id = slotKey(this),
        pattern = pattern,
        primaryMuscle = primaryMuscle,
        role = role,
        repProfile = repProfile,
        allowedDomains = allowedDomainsForSlot(this),
        isCrossDomainTouch = isCrossDomainTouch,
        siblingVariant = siblingVariant,
    )

internal fun allowedDomainsForSlot(slot: WorkoutSlotTemplate): Set<SynthesisSessionDomain> =
    allowedDomainsForPattern(slot.pattern, slot.primaryMuscle, slot.isCrossDomainTouch)

internal fun allowedDomainsForPattern(
    pattern: String,
    primaryMuscle: String,
    isCrossDomainTouch: Boolean,
): Set<SynthesisSessionDomain> {
    if (isCrossDomainTouch) {
        return when (muscleTrainingDomain(primaryMuscle)) {
            TrainingDomain.Lower -> setOf(SynthesisSessionDomain.Upper, SynthesisSessionDomain.Full)
            TrainingDomain.Upper -> setOf(SynthesisSessionDomain.Lower, SynthesisSessionDomain.Full)
        }
    }
    if (pattern in LOWER_PRIMARY_PATTERNS || muscleTrainingDomain(primaryMuscle) == TrainingDomain.Lower) {
        return setOf(SynthesisSessionDomain.Lower, SynthesisSessionDomain.Full)
    }
    return setOf(SynthesisSessionDomain.Upper, SynthesisSessionDomain.Full)
}

fun isSlotCompatibleWithDomain(
    slot: SynthesisSlot,
    domain: SynthesisSessionDomain,
): Boolean = domain in slot.allowedDomains

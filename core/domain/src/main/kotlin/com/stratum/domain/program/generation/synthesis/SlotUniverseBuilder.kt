@file:Suppress("MagicNumber")

package com.stratum.domain.program.generation.synthesis

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.split.SessionComposition
import com.stratum.domain.program.generation.split.SessionTypeId
import com.stratum.domain.program.generation.split.SlotRole
import com.stratum.domain.program.generation.split.WorkoutSlotTemplate
import com.stratum.domain.program.generation.split.slotsForSessionType
import com.stratum.domain.program.generation.selection.candidatesForSlot
import com.stratum.domain.program.generation.volume.TrainingDomain
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets
import com.stratum.domain.program.generation.volume.muscleTrainingDomain

private const val MAX_UNIVERSE_SLOTS = 80

private val LOWER_PRIMARY_PATTERNS =
    setOf(
        MovementPattern.SquatPattern,
        MovementPattern.HipHinge,
        MovementPattern.HipExtension,
        MovementPattern.KneeExtension,
        MovementPattern.KneeFlexion,
        MovementPattern.PlantarFlexion,
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
            .filterValues { level -> level == SpecializationLevel.Specialize || level == SpecializationLevel.MinimumVolume }
            .keys
            .map { it.name }
            .toSet()
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
    if (slot.pattern == MovementPattern.VerticalPress && slot.primaryMuscle == MuscleGroup.AnteriorDeltoid &&
        context.specialization[MuscleGroup.AnteriorDeltoid] != SpecializationLevel.Specialize
    ) {
        return false
    }
    // Glute-primary slot (HipExtension) is excluded for Maintenance users — glute volume rides on
    // squat/hinge secondaries, so a dedicated hip-thrust slot churns session time without aim-driven
    // need. Included only when the user Specializes Glute (per the "keep out unless specializing"
    // policy). Other lower anchors (squat/hinge) stay in regardless because they target Quad/Hamstring.
    if (slot.pattern == MovementPattern.HipExtension && slot.primaryMuscle == MuscleGroup.Glute &&
        context.specialization[MuscleGroup.Glute] != SpecializationLevel.Specialize
    ) {
        return false
    }
    val keepsByMuscleOrRole =
        slot.primaryMuscle.name in relevantMuscles ||
            context.specialization[slot.primaryMuscle] == SpecializationLevel.Specialize ||
            slot.role == SlotRole.Anchor
    if (!keepsByMuscleOrRole) return false
    // Candidate existence (audit §4.4): never include a slot with zero enabled catalog candidates.
    // `candidatesForSlot` is stricter than `availablePatternsByMuscle` (strict primary-muscle match
    // for Anchor/Isolation/touch), so a slot can look available-by-pattern yet have no realizable
    // lift — activating it would yield a blocking `UNSELECTED_ACTIVE_SLOT` after a long solve.
    return candidatesForSlot(slot, context).isNotEmpty()
}

private fun slotKey(slot: WorkoutSlotTemplate): String =
    listOf(
        slot.pattern.name,
        slot.primaryMuscle.name,
        slot.role.name,
        slot.isCrossDomainTouch.toString(),
        slot.siblingVariant?.name ?: "Any",
    ).joinToString("|")

private fun WorkoutSlotTemplate.toSynthesisSlot(): SynthesisSlot =
    SynthesisSlot(
        id = slotKey(this),
        pattern = pattern.name,
        primaryMuscle = primaryMuscle.name,
        role = role,
        repProfile = repProfile,
        allowedDomains = allowedDomainsForSlot(this),
        isCrossDomainTouch = isCrossDomainTouch,
        siblingVariant = siblingVariant,
    )

internal fun allowedDomainsForSlot(slot: WorkoutSlotTemplate): Set<SynthesisSessionDomain> =
    allowedDomainsForPattern(slot.pattern.name, slot.primaryMuscle.name, slot.isCrossDomainTouch)

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
    if (runCatching { MovementPattern.valueOf(pattern) }.getOrNull() in LOWER_PRIMARY_PATTERNS ||
        muscleTrainingDomain(primaryMuscle) == TrainingDomain.Lower
    ) {
        return setOf(SynthesisSessionDomain.Lower, SynthesisSessionDomain.Full)
    }
    return setOf(SynthesisSessionDomain.Upper, SynthesisSessionDomain.Full)
}

fun isSlotCompatibleWithDomain(
    slot: SynthesisSlot,
    domain: SynthesisSessionDomain,
): Boolean = domain in slot.allowedDomains

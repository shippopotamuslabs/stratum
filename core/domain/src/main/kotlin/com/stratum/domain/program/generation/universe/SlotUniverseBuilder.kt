@file:Suppress("MagicNumber")

package com.stratum.domain.program.generation.universe

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.selection.candidatesForSlot
import com.stratum.domain.program.generation.slots.SlotGenerationDiagnostic
import com.stratum.domain.program.generation.slots.SlotRole
import com.stratum.domain.program.generation.slots.WorkoutSlotTemplate
import com.stratum.domain.program.generation.slots.baseSlotLibrary
import com.stratum.domain.program.generation.slots.crossDomainTouches
import com.stratum.domain.program.generation.slots.injectSpecializedVariantSlots
import com.stratum.domain.program.generation.slots.librarySlotKey
import com.stratum.domain.program.generation.slots.promoteSpecializedSlots
import com.stratum.domain.program.generation.structure.allowedRegionFiltersForSlot
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets
import com.stratum.domain.program.generation.slots.SessionTypeId
import com.stratum.domain.program.generation.volume.availablePatternsByMuscle

private const val MAX_UNIVERSE_SLOTS = 80

/**
 * Builds the realizable slot universe for CP-SAT from the explicit [baseSlotLibrary],
 * specialized sibling variants, and cross-domain touch candidates — not a loop over
 * [com.stratum.domain.program.generation.slots.SessionTypeId].
 *
 * Pipeline:
 * ```
 * baseSlotLibrary()
 *   + specializedVariants(context)
 *   + crossDomainTouches(context)
 *   .filter(isSlotRelevant)
 *   .distinctBy(slotKey)
 * ```
 */
fun buildSlotUniverse(
    context: ProgramGenerationContext,
    weeklyTargets: WeeklyVolumeTargets,
    diagnostics: MutableList<SlotGenerationDiagnostic>? = null,
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

    val base = baseSlotLibrary()
    val withVariants = specializedVariants(base, context)
    val touches = crossDomainTouches(context, diagnostics)

    val raw = linkedMapOf<String, WorkoutSlotTemplate>()
    for (slot in withVariants + touches) {
        if (!isSlotRelevant(slot, relevantMuscles, context)) continue
        val key = slotKey(slot)
        if (key !in raw) {
            raw[key] = slot
        }
    }

    return raw.values
        .map { slot -> slot.toSynthesisSlot() }
        .take(MAX_UNIVERSE_SLOTS)
}

/**
 * Promote specialized optionals and inject sibling-B variants when the user specializes
 * a muscle that has only one in-domain library slot and multiple lift options.
 */
internal fun specializedVariants(
    base: List<WorkoutSlotTemplate>,
    context: ProgramGenerationContext,
): List<WorkoutSlotTemplate> {
    val slots = base.map { it.copy() }.toMutableList()
    promoteSpecializedSlots(slots, context)
    injectSpecializedVariantSlots(slots, context)
    return slots
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

internal fun slotKey(slot: WorkoutSlotTemplate): String = librarySlotKey(slot)

private fun WorkoutSlotTemplate.toSynthesisSlot(): SynthesisSlot =
    SynthesisSlot(
        id = slotKey(this),
        pattern = pattern.name,
        primaryMuscle = primaryMuscle.name,
        role = role,
        repProfile = repProfile,
        allowedRegionFilters = allowedRegionFiltersForSlot(this),
        isCrossDomainTouch = isCrossDomainTouch,
        siblingVariant = siblingVariant,
        lengthBiasPreference = lengthBiasPreference,
        allowedFatigueProfiles = allowedFatigueProfiles,
    )

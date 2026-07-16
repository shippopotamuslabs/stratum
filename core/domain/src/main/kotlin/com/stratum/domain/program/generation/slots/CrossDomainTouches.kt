@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.slots

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.volume.TrainingDomain
import com.stratum.domain.program.generation.volume.availablePatternsByMuscle
import com.stratum.domain.program.generation.volume.sessionTrainingDomain

/** Preferred isolation movement patterns per muscle for cross-domain touch slots. */
private val TOUCH_ISOLATION_PATTERNS: Map<String, List<String>> = mapOf(
    "Bicep" to listOf("ElbowFlexion"),
    "Tricep" to listOf("ElbowExtension"),
    "Calf" to listOf("PlantarFlexion"),
    "LateralDeltoid" to listOf("ShoulderAbduction"),
    "PosteriorDeltoid" to listOf("ShoulderHorizontalAbduction"),
    "AnteriorDeltoid" to listOf("VerticalPress"),
    "Chest" to listOf("HorizontalPress", "InclinePress"),
    "Back" to listOf("HorizontalPull", "VerticalPull"),
    "Quad" to listOf("KneeExtension", "SquatPattern"),
    "Hamstring" to listOf("KneeFlexion", "HipHinge"),
    "Glute" to listOf("HipExtension", "HipHinge"),
)

private const val MAX_TOUCH_SLOTS_PER_SESSION = 2
private const val MAX_TOUCH_SLOTS_ON_LOWER_WITH_IN_DOMAIN_SPEC = 1

/**
 * Adds optional isolation slots on sessions outside a specialized muscle's primary domain
 * so CP-SAT can allocate supplementary direct volume (e.g. arms on a lower day).
 *
 * Legacy session-plan path: still keyed by [SessionTypeId] for slot ids used in assembled sessions.
 */
internal fun injectCrossDomainTouchSlots(
    sessionType: SessionTypeId,
    slots: MutableList<WorkoutSlotTemplate>,
    context: ProgramGenerationContext,
    sessionPosition: Int? = null,
    composition: SessionComposition? = null,
) {
    injectCrossDomainTouchSlots(
        slots = slots,
        context = context,
        sessionDomain = sessionTrainingDomain(sessionType),
        sessionPosition = sessionPosition,
        composition = composition,
        touchSlotId = { muscle -> "touch-${muscle.name.lowercase()}-${sessionType.name.lowercase()}" },
    )
}

/**
 * Domain-driven cross-domain touch injection (no [SessionTypeId] in identity).
 * Used by universe construction and any caller that only has region/domain context.
 */
internal fun injectCrossDomainTouchSlots(
    slots: MutableList<WorkoutSlotTemplate>,
    context: ProgramGenerationContext,
    sessionDomain: TrainingDomain?,
    sessionPosition: Int? = null,
    composition: SessionComposition? = null,
    touchSlotId: (MuscleGroup) -> String = { muscle -> "touch-${muscle.name.lowercase()}" },
) {
    val sessionPrimaries = slots.map { it.primaryMuscle }.toSet()
    val patternsByMuscle = availablePatternsByMuscle(context)
    var touchCount = slots.count { it.isCrossDomainTouch }
    val touchBudget = maxTouchSlotsForDomain(sessionDomain, slots, context, composition)

    for ((muscle, level) in context.specialization) {
        if (level != SpecializationLevel.Specialize) continue
        if (muscle in sessionPrimaries) continue
        if (muscle == MuscleGroup.Calf &&
            !allowsCalfCrossDomainTouch(sessionDomain, sessionPosition, context, composition)
        ) {
            continue
        }
        if (touchCount >= touchBudget) break
        if (slots.any { it.isCrossDomainTouch && it.primaryMuscle == muscle }) continue

        val pattern = resolveTouchPattern(muscle.name, patternsByMuscle) ?: continue
        val slotId = touchSlotId(muscle)
        if (slots.any { it.slotId == slotId }) continue

        val touchSlot = WorkoutSlotTemplate(
            slotId = slotId,
            role = SlotRole.Accessory,
            pattern = runCatching { MovementPattern.valueOf(pattern) }.getOrNull() ?: continue,
            primaryMuscle = muscle,
            repProfile = RpRepProfile.Isolation,
            isCrossDomainTouch = true,
        )
        val insertAt = touchSlotInsertIndex(slots, muscle.name, pattern)
        slots.add(insertAt, touchSlot)
        touchCount++
    }
}

/**
 * Universe-level cross-domain touches driven by [context] specialization and domain shells
 * derived from base library catalog groups (not [SessionTypeId] names in slot ids).
 *
 * Domain shells align with former SOURCE_SESSION_TYPES contributions so candidate existence
 * (which touch keys appear) matches the pre–slot-library universe. Touch [slotId]s are stable
 * (`touch-{muscle}`); universe identity is [librarySlotKey] / pattern|muscle|role|touch|variant.
 */
internal fun crossDomainTouches(
    context: ProgramGenerationContext,
): List<WorkoutSlotTemplate> {
    // Shells mirror the five former source catalogs + compositions used by buildSlotUniverse.
    data class DomainShell(
        val templates: List<WorkoutSlotTemplate>,
        val domain: TrainingDomain?,
        val composition: SessionComposition?,
        val sessionPosition: Int = 0,
    )

    val shells =
        listOf(
            DomainShell(UPPER_SLOTS, TrainingDomain.Upper, SessionComposition.UpperBalanced, sessionPosition = 0),
            DomainShell(LOWER_SLOTS, TrainingDomain.Lower, SessionComposition.LowerPrimary, sessionPosition = 0),
            DomainShell(FULL_BODY_SLOTS, domain = null, composition = null, sessionPosition = 0),
            DomainShell(UPPER_PUSH_SLOTS, TrainingDomain.Upper, composition = null, sessionPosition = 0),
            DomainShell(UPPER_PULL_SLOTS, TrainingDomain.Upper, composition = null, sessionPosition = 0),
        )

    val byKey = linkedMapOf<String, WorkoutSlotTemplate>()
    for (shell in shells) {
        val shellSlots = shell.templates.map { it.copy() }.toMutableList()
        promoteSpecializedSlots(shellSlots, context)
        injectCrossDomainTouchSlots(
            slots = shellSlots,
            context = context,
            sessionDomain = shell.domain,
            sessionPosition = shell.sessionPosition,
            composition = shell.composition,
            touchSlotId = { muscle -> "touch-${muscle.name.lowercase()}" },
        )
        for (slot in shellSlots) {
            if (!slot.isCrossDomainTouch) continue
            val key = librarySlotKey(slot)
            if (key !in byKey) {
                byKey[key] = slot
            }
        }
    }
    return byKey.values.toList()
}

private fun resolveTouchPattern(
    muscle: String,
    patternsByMuscle: Map<String, List<String>>,
): String? {
    val available = patternsByMuscle[muscle].orEmpty().toSet()
    if (available.isEmpty()) return null
    val preferred = TOUCH_ISOLATION_PATTERNS[muscle].orEmpty()
    return preferred.firstOrNull { it in available }
        ?: available.firstOrNull { pattern ->
            pattern !in setOf("SquatPattern", "HipHinge", "HorizontalPress", "VerticalPress", "InclinePress")
        }
        ?: available.firstOrNull()
}

/** Insert after a compatible anchor to preserve antagonistic interleaving. */
private fun touchSlotInsertIndex(
    slots: List<WorkoutSlotTemplate>,
    muscle: String,
    pattern: String,
): Int {
    val anchorPattern = when {
        muscle in setOf("Bicep", "Back") -> "HipHinge"
        muscle == "Tricep" -> "SquatPattern"
        muscle in setOf("Quad", "Hamstring", "Glute", "Calf") -> "HorizontalPress"
        muscle in setOf("Chest", "AnteriorDeltoid", "LateralDeltoid", "PosteriorDeltoid") -> "HipHinge"
        else -> null
    }
    if (anchorPattern != null) {
        val anchorIdx = slots.indexOfLast { it.pattern.name == anchorPattern }
        if (anchorIdx >= 0) return anchorIdx + 1
    }
    val lastCompound = slots.indexOfLast {
        it.role == SlotRole.Anchor || it.role == SlotRole.SecondaryCompound
    }
    if (lastCompound >= 0) return lastCompound + 1
    return slots.size
}

/**
 * On 5–6 day rolling splits, calf touch on every upper day creates unschedulable
 * recovery streaks — one upper slot per cycle plus lower-day isolation is enough.
 */
private fun allowsCalfCrossDomainTouch(
    sessionDomain: TrainingDomain?,
    sessionPosition: Int?,
    context: ProgramGenerationContext,
    composition: SessionComposition? = null,
): Boolean {
    if (composition == SessionComposition.UpperSpecFocused) return true
    if (context.trainingDaysPerWeek < 5) return true
    if (sessionDomain != TrainingDomain.Upper) return true
    if (sessionPosition == null) return true
    // Alternating lower-first weekly cycles place the first upper at index 1.
    return sessionPosition == 1
}

/**
 * Lower days with in-domain specialization (e.g. calves) need squat/hinge budget.
 * Cap cross-domain touches so arm iso does not crowd out leg compounds.
 */
internal fun maxTouchSlotsForSession(
    sessionType: SessionTypeId,
    slots: List<WorkoutSlotTemplate>,
    context: ProgramGenerationContext,
    composition: SessionComposition? = null,
): Int = maxTouchSlotsForDomain(sessionTrainingDomain(sessionType), slots, context, composition)

internal fun maxTouchSlotsForDomain(
    sessionDomain: TrainingDomain?,
    slots: List<WorkoutSlotTemplate>,
    context: ProgramGenerationContext,
    composition: SessionComposition? = null,
): Int {
    if (composition == SessionComposition.LowerWithUpperTouch) {
        return MAX_TOUCH_SLOTS_PER_SESSION
    }
    if (sessionDomain != TrainingDomain.Lower) {
        return MAX_TOUCH_SLOTS_PER_SESSION
    }
    val hasInDomainSpecialized =
        slots.any { slot ->
            !slot.isCrossDomainTouch &&
                context.specialization[slot.primaryMuscle] == SpecializationLevel.Specialize
        }
    return if (hasInDomainSpecialized) MAX_TOUCH_SLOTS_ON_LOWER_WITH_IN_DOMAIN_SPEC else MAX_TOUCH_SLOTS_PER_SESSION
}

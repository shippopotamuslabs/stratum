@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.split

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
 */
internal fun injectCrossDomainTouchSlots(
    sessionType: SessionTypeId,
    slots: MutableList<WorkoutSlotTemplate>,
    context: ProgramGenerationContext,
    sessionPosition: Int? = null,
    composition: SessionComposition? = null,
) {
    val sessionPrimaries = slots.map { it.primaryMuscle }.toSet()
    val patternsByMuscle = availablePatternsByMuscle(context)
    var touchCount = slots.count { it.isCrossDomainTouch }
    val touchBudget = maxTouchSlotsForSession(sessionType, slots, context, composition)

    for ((muscle, level) in context.specialization) {
        if (level != "Specialize") continue
        if (muscle in sessionPrimaries) continue
        if (muscle == "Calf" && !allowsCalfCrossDomainTouch(sessionType, sessionPosition, context, composition)) {
            continue
        }
        if (touchCount >= touchBudget) break
        if (slots.any { it.isCrossDomainTouch && it.primaryMuscle == muscle }) continue

        val pattern = resolveTouchPattern(muscle, patternsByMuscle) ?: continue
        val slotId = "touch-${muscle.lowercase()}-${sessionType.name.lowercase()}"
        if (slots.any { it.slotId == slotId }) continue

        val touchSlot = WorkoutSlotTemplate(
            slotId = slotId,
            role = SlotRole.Isolation,
            pattern = pattern,
            primaryMuscle = muscle,
            repProfile = RpRepProfile.Isolation,
            isCrossDomainTouch = true,
        )
        val insertAt = touchSlotInsertIndex(slots, muscle, pattern)
        slots.add(insertAt, touchSlot)
        touchCount++
    }
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
        val anchorIdx = slots.indexOfLast { it.pattern == anchorPattern }
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
    sessionType: SessionTypeId,
    sessionPosition: Int?,
    context: ProgramGenerationContext,
    composition: SessionComposition? = null,
): Boolean {
    if (composition == SessionComposition.UpperSpecFocused) return true
    if (context.trainingDaysPerWeek < 5) return true
    if (sessionTrainingDomain(sessionType) != TrainingDomain.Upper) return true
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
): Int {
    if (composition == SessionComposition.LowerWithUpperTouch) {
        return MAX_TOUCH_SLOTS_PER_SESSION
    }
    if (sessionTrainingDomain(sessionType) != TrainingDomain.Lower) {
        return MAX_TOUCH_SLOTS_PER_SESSION
    }
    val hasInDomainSpecialized =
        slots.any { slot ->
            !slot.isCrossDomainTouch &&
                (context.specialization[slot.primaryMuscle] ?: "Maintenance") == "Specialize"
        }
    return if (hasInDomainSpecialized) MAX_TOUCH_SLOTS_ON_LOWER_WITH_IN_DOMAIN_SPEC else MAX_TOUCH_SLOTS_PER_SESSION
}

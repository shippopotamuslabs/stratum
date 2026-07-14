@file:Suppress("MagicNumber")

package com.stratum.domain.program.generation.synthesis

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.generation.prescription.FilledSlot
import com.stratum.domain.program.generation.prescription.SessionSlotPlan
import com.stratum.domain.program.generation.split.ResolvedSessionPlan
import com.stratum.domain.program.generation.slots.SessionTypeId
import com.stratum.domain.program.generation.slots.WorkoutSlotTemplate
import com.stratum.domain.program.generation.structure.SessionRegionFilter
import com.stratum.domain.program.generation.structure.allowedRegionFiltersForPattern
import com.stratum.domain.program.generation.universe.SynthesisSlot
import com.stratum.domain.program.generation.volume.TrainingDomain
import com.stratum.domain.program.generation.volume.muscleTrainingDomain


private fun patternFromSynthesis(pattern: String): MovementPattern =
    runCatching { MovementPattern.valueOf(pattern) }.getOrNull()
        ?: error("Unknown MovementPattern: $pattern")

private fun muscleFromSynthesis(muscle: String): MuscleGroup =
    runCatching { MuscleGroup.valueOf(muscle) }.getOrNull()
        ?: error("Unknown MuscleGroup: $muscle")

fun buildUnifiedSessionPlans(
    universe: List<SynthesisSlot>,
    sessionCount: Int,
): List<SessionSlotPlan> =
    (0 until sessionCount).map { sessionIndex ->
        SessionSlotPlan(
            sessionType = SessionTypeId.FullBody,
            name = placeholderWorkoutName(sessionIndex),
            slots = emptyList(),
            siblingIndex = 0,
            position = sessionIndex,
            filledSlots =
                universe
                    .map { slot ->
                        FilledSlot(
                            slotId = sessionSlotId(sessionIndex, slot.id),
                            role = slot.role,
                            pattern = patternFromSynthesis(slot.pattern),
                            primaryMuscle = muscleFromSynthesis(slot.primaryMuscle),
                            repProfile = slot.repProfile,
                            lengthBiasPreference = slot.lengthBiasPreference,
                            siblingVariant = slot.siblingVariant,
                            isCrossDomainTouch = slot.isCrossDomainTouch,
                            allowedKinematicProfiles = slot.allowedKinematicProfiles,
                            setCount = 0,
                        )
                    }
                    .toMutableList(),
        )
    }

fun sessionSlotId(sessionIndex: Int, universeSlotId: String): String = "s${sessionIndex}_$universeSlotId"

fun parseSessionSlotId(slotId: String): Pair<Int, String>? {
    if (!slotId.startsWith("s")) return null
    val underscore = slotId.indexOf('_')
    if (underscore <= 1) return null
    val sessionIndex = slotId.substring(1, underscore).toIntOrNull() ?: return null
    return sessionIndex to slotId.substring(underscore + 1)
}

/**
 * Realizes a solved [UnifiedSynthesisSolution] into dual plan/session lists for the pipeline.
 *
 * **Structure truth** is [UnifiedSynthesisSolution.regionFilterBySession] (and active slots /
 * set counts). [SessionTypeId] on plans/sessions is a **legacy display label only** — mapped
 * from the region filter when present — not a split-type identity and not used for recovery or
 * variety math.
 *
 * Dual-write of [ProgramSynthesisResult.plans] ([SessionSlotPlan] with filled slots for lift
 * selection) and [ProgramSynthesisResult.sessions] ([ResolvedSessionPlan] for wire / cycleLength
 * consumers) is intentional; keep both for the cloud codec contract.
 */
fun materializeSynthesisResult(
    universe: List<SynthesisSlot>,
    solution: UnifiedSynthesisSolution,
): ProgramSynthesisResult {
    val universeById = universe.associateBy { slot -> slot.id }
    val plans = mutableListOf<SessionSlotPlan>()
    val sessions = mutableListOf<ResolvedSessionPlan>()
    val sessionCount = solution.activeSlotIdsBySession.size

    for (sessionIndex in 0 until sessionCount) {
        val activeIds = solution.activeSlotIdsBySession.getOrElse(sessionIndex) { emptySet() }
        val activeSlots =
            activeIds.mapNotNull { universeId ->
                universeById[universeId]?.let { slot ->
                    val setCount = solution.setCountBySessionSlot[sessionIndex to universeId] ?: 0
                    if (setCount <= 0) return@mapNotNull null
                    slot to setCount
                }
            }
        val templates =
            activeSlots.map { (slot, setCount) ->
                WorkoutSlotTemplate(
                    slotId = sessionSlotId(sessionIndex, slot.id),
                    role = slot.role,
                    pattern = patternFromSynthesis(slot.pattern),
                    primaryMuscle = muscleFromSynthesis(slot.primaryMuscle),
                    repProfile = slot.repProfile,
                    lengthBiasPreference = slot.lengthBiasPreference,
                    isCrossDomainTouch = slot.isCrossDomainTouch,
                    siblingVariant = slot.siblingVariant,
                    allowedKinematicProfiles = slot.allowedKinematicProfiles,
                ) to setCount
            }
        // Prefer solver region filter for the legacy SessionTypeId label; fall back to slot
        // composition only when filters are short/missing (should not happen on real solver path).
        val sessionType =
            sessionTypeLabelForSession(
                sessionIndex = sessionIndex,
                regionFilterBySession = solution.regionFilterBySession,
                activeSlots = activeSlots.map { it.first },
            )
        val filled =
            templates.map { (template, setCount) ->
                FilledSlot(
                    slotId = template.slotId,
                    role = template.role,
                    pattern = template.pattern,
                    primaryMuscle = template.primaryMuscle,
                    repProfile = template.repProfile,
                    lengthBiasPreference = template.lengthBiasPreference,
                    isCrossDomainTouch = template.isCrossDomainTouch,
                    siblingVariant = template.siblingVariant,
                    allowedKinematicProfiles = template.allowedKinematicProfiles,
                    setCount = setCount,
                )
            }
        plans +=
            SessionSlotPlan(
                sessionType = sessionType,
                name = placeholderWorkoutName(sessionIndex),
                slots = templates.map { it.first },
                siblingIndex = 0,
                position = sessionIndex,
                filledSlots = filled.toMutableList(),
            )
        sessions +=
            ResolvedSessionPlan(
                sessionType = sessionType,
                name = placeholderWorkoutName(sessionIndex),
                slots = templates.map { it.first },
                siblingIndex = 0,
                position = sessionIndex,
                composition = null,
            )
    }

    return ProgramSynthesisResult(
        sessions = sessions,
        plans = plans,
        cycleSlotIndexByPosition = solution.cycleSlotIndexByPosition,
        objective = com.stratum.domain.program.generation.prescription.SynthesisObjectiveBreakdown.ZERO,
        activeSlotIdsBySession = solution.activeSlotIdsBySession.map { ids -> ids.toSet() },
        // Pass through solver region filters — source of truth (do not drop).
        regionFilterBySession = solution.regionFilterBySession,
    )
}

/**
 * Maps solver [SessionRegionFilter] → legacy [SessionTypeId] display label.
 *
 * | Region filter | Label |
 * |---------------|-------|
 * | UpperOnly | Upper |
 * | LowerOnly | Lower |
 * | Unrestricted | FullBody |
 *
 * This is **not** structure truth — [SessionRegionFilter] / `regionFilterBySession` is.
 */
fun sessionTypeLabelFromRegionFilter(filter: SessionRegionFilter): SessionTypeId =
    when (filter) {
        SessionRegionFilter.UpperOnly -> SessionTypeId.Upper
        SessionRegionFilter.LowerOnly -> SessionTypeId.Lower
        SessionRegionFilter.Unrestricted -> SessionTypeId.FullBody
    }

internal fun sessionTypeLabelForSession(
    sessionIndex: Int,
    regionFilterBySession: List<SessionRegionFilter>,
    activeSlots: List<SynthesisSlot>,
): SessionTypeId {
    val filter = regionFilterBySession.getOrNull(sessionIndex)
    if (filter != null) return sessionTypeLabelFromRegionFilter(filter)
    return inferSessionTypeFromActiveSlots(activeSlots)
}

/**
 * Fallback only: infer a legacy [SessionTypeId] label from active slot composition when
 * [regionFilterBySession] is short/missing. Prefer [sessionTypeLabelFromRegionFilter].
 */
internal fun inferSessionTypeFromActiveSlots(slots: List<SynthesisSlot>): SessionTypeId {
    if (slots.isEmpty()) return SessionTypeId.FullBody
    val patterns = slots.map { it.pattern }.toSet()
    val lowerCount =
        slots.count { slot -> muscleTrainingDomain(slot.primaryMuscle) == TrainingDomain.Lower }
    val upperCount = slots.size - lowerCount
    return when {
        lowerCount > 0 && upperCount == 0 -> SessionTypeId.Lower
        upperCount > 0 && lowerCount == 0 -> SessionTypeId.Upper
        "SquatPattern" in patterns || "HipHinge" in patterns -> SessionTypeId.Lower
        "HorizontalPress" in patterns || "VerticalPull" in patterns -> SessionTypeId.Upper
        else -> SessionTypeId.FullBody
    }
}

fun inferSessionTypeFromSlots(slotTemplates: List<WorkoutSlotTemplate>): SessionTypeId {
    val regionFilters =
        slotTemplates.map { template ->
            allowedRegionFiltersForPattern(
                template.pattern.name,
                template.primaryMuscle.name,
                template.isCrossDomainTouch,
            )
        }
    return when {
        regionFilters.all {
            SessionRegionFilter.LowerOnly in it && SessionRegionFilter.UpperOnly !in it
        } -> SessionTypeId.Lower
        regionFilters.all {
            SessionRegionFilter.UpperOnly in it && SessionRegionFilter.LowerOnly !in it
        } -> SessionTypeId.Upper
        else -> SessionTypeId.FullBody
    }
}

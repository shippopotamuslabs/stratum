@file:Suppress("MagicNumber")

package com.stratum.domain.program.generation.synthesis

import com.stratum.domain.program.generation.prescription.FilledSlot
import com.stratum.domain.program.generation.prescription.SessionSlotPlan
import com.stratum.domain.program.generation.split.ResolvedSessionPlan
import com.stratum.domain.program.generation.split.SessionTypeId
import com.stratum.domain.program.generation.split.WorkoutSlotTemplate
import com.stratum.domain.program.generation.volume.TrainingDomain
import com.stratum.domain.program.generation.volume.muscleTrainingDomain

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
                            pattern = slot.pattern,
                            primaryMuscle = slot.primaryMuscle,
                            repProfile = slot.repProfile,
                            lengthBiasPreference = null,
                            siblingVariant = slot.siblingVariant,
                            isCrossDomainTouch = slot.isCrossDomainTouch,
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
                    pattern = slot.pattern,
                    primaryMuscle = slot.primaryMuscle,
                    repProfile = slot.repProfile,
                    isCrossDomainTouch = slot.isCrossDomainTouch,
                    siblingVariant = slot.siblingVariant,
                ) to setCount
            }
        val sessionType = inferSessionTypeFromActiveSlots(activeSlots.map { it.first })
        val filled =
            templates.map { (template, setCount) ->
                FilledSlot(
                    slotId = template.slotId,
                    role = template.role,
                    pattern = template.pattern,
                    primaryMuscle = template.primaryMuscle,
                    repProfile = template.repProfile,
                    isCrossDomainTouch = template.isCrossDomainTouch,
                    siblingVariant = template.siblingVariant,
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
        weekdayIndexByPosition = solution.weekdayIndexByPosition,
        objective = com.stratum.domain.program.generation.prescription.SynthesisObjectiveBreakdown.ZERO,
        activeSlotIdsBySession = solution.activeSlotIdsBySession.map { ids -> ids.toSet() },
    )
}

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
    val domains =
        slotTemplates.map { template ->
            allowedDomainsForPattern(template.pattern, template.primaryMuscle, template.isCrossDomainTouch)
        }
    return when {
        domains.all { SynthesisSessionDomain.Lower in it && SynthesisSessionDomain.Upper !in it } ->
            SessionTypeId.Lower
        domains.all { SynthesisSessionDomain.Upper in it && SynthesisSessionDomain.Lower !in it } ->
            SessionTypeId.Upper
        else -> SessionTypeId.FullBody
    }
}

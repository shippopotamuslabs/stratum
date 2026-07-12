package com.stratum.core.testing.program

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.selection.candidatesForSlot
import com.stratum.domain.program.generation.split.WorkoutSlotTemplate
import com.stratum.domain.program.generation.split.trainingWeekdayIndicesInUserOrder
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisPort
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisResult
import com.stratum.domain.program.generation.synthesis.SynthesisSessionDomain
import com.stratum.domain.program.generation.synthesis.SynthesisSlot
import com.stratum.domain.program.generation.synthesis.UnifiedSynthesisSolution
import com.stratum.domain.program.generation.synthesis.buildSlotUniverse
import com.stratum.domain.program.generation.synthesis.materializeSynthesisResult
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets

/**
 * Deterministic, native-free [ProgramSynthesisPort] for JVM unit tests.
 *
 * Builds a valid slot universe and greedily activates slots that have catalog candidates.
 * Real CP-SAT synthesis lives behind [com.stratum.domain.solver.CpSatProgramSynthesizer]
 * and is exercised only in `:core:domain-solver-tests` (host JVM OR-Tools natives).
 */
class FakeProgramSynthesisPort(
    private val setsPerSlot: Int = DEFAULT_SETS,
    private val targetSlotsPerSession: Int = DEFAULT_SLOTS_PER_SESSION,
) : ProgramSynthesisPort {

    override suspend fun synthesize(
        weeklyTargets: WeeklyVolumeTargets,
        context: ProgramGenerationContext,
    ): ProgramSynthesisResult {
        val universe = buildSlotUniverse(context, weeklyTargets)
        require(universe.isNotEmpty()) { "Slot universe is empty for fake synthesis." }

        val selectable =
            universe.filter { slot ->
                candidatesForSlot(slot.toTemplate(), context).isNotEmpty()
            }
        require(selectable.isNotEmpty()) {
            "No selectable slots for fake synthesis (catalog has no pattern matches)."
        }

        val sessionCount = context.trainingDaysPerWeek.coerceAtLeast(1)
        val weekdayIndices = weekdayIndicesForSessions(context.weekdays, sessionCount)

        val activeBySession = List(sessionCount) { linkedSetOf<String>() }
        val setCounts = linkedMapOf<Pair<Int, String>, Int>()

        // Round-robin fill so each session gets distinct work when the universe allows it.
        var cursor = 0
        for (sessionIndex in 0 until sessionCount) {
            var filled = 0
            var attempts = 0
            val maxAttempts = selectable.size * 2
            while (filled < targetSlotsPerSession && attempts < maxAttempts) {
                val slot = selectable[cursor % selectable.size]
                cursor++
                attempts++
                if (!activeBySession[sessionIndex].add(slot.id)) continue
                setCounts[sessionIndex to slot.id] = setsPerSlot
                filled++
            }
            // Guarantee at least one slot so workouts are never empty.
            if (activeBySession[sessionIndex].isEmpty()) {
                val slot = selectable[sessionIndex % selectable.size]
                activeBySession[sessionIndex].add(slot.id)
                setCounts[sessionIndex to slot.id] = setsPerSlot
            }
        }

        val solution =
            UnifiedSynthesisSolution(
                activeSlotIdsBySession = activeBySession.map { ids -> ids.toSet() },
                setCountBySessionSlot = setCounts,
                weekdayIndexByPosition = weekdayIndices,
                domainBySession = List(sessionCount) { SynthesisSessionDomain.Full },
            )
        return materializeSynthesisResult(universe, solution)
    }

    private fun weekdayIndicesForSessions(
        weekdays: List<String>,
        sessionCount: Int,
    ): List<Int> {
        val fromContext = trainingWeekdayIndicesInUserOrder(weekdays)
        if (fromContext.size >= sessionCount) {
            return fromContext.take(sessionCount)
        }
        if (fromContext.isEmpty()) {
            return List(sessionCount) { index -> index % 7 }
        }
        return List(sessionCount) { index ->
            fromContext.getOrElse(index) { fromContext[index % fromContext.size] }
        }
    }

    private fun SynthesisSlot.toTemplate(): WorkoutSlotTemplate =
        WorkoutSlotTemplate(
            slotId = id,
            role = role,
            pattern = MovementPattern.valueOf(pattern),
            primaryMuscle = MuscleGroup.valueOf(primaryMuscle),
            repProfile = repProfile,
            siblingVariant = siblingVariant,
            isCrossDomainTouch = isCrossDomainTouch,
        )

    private companion object {
        const val DEFAULT_SETS = 3
        const val DEFAULT_SLOTS_PER_SESSION = 6
    }
}

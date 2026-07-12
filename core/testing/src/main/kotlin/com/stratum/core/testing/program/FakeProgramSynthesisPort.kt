package com.stratum.core.testing.program

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.selection.candidatesForSlot
import com.stratum.domain.program.generation.slots.WorkoutSlotTemplate
import com.stratum.domain.program.generation.split.trainingWeekdayIndicesInUserOrder
import com.stratum.domain.program.generation.structure.SessionRegionFilter
import com.stratum.domain.program.generation.structure.isSlotCompatibleWithRegionFilter
import com.stratum.domain.program.generation.structure.regionFiltersBySessionPosition
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisPort
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisResult
import com.stratum.domain.program.generation.synthesis.UnifiedSynthesisSolution
import com.stratum.domain.program.generation.synthesis.materializeSynthesisResult
import com.stratum.domain.program.generation.universe.SynthesisSlot
import com.stratum.domain.program.generation.universe.buildSlotUniverse
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
        val regionFilters = regionFiltersForSessions(context.weekdays, sessionCount)

        val activeBySession = List(sessionCount) { linkedSetOf<String>() }
        val setCounts = linkedMapOf<Pair<Int, String>, Int>()

        // Round-robin fill so each session gets distinct work when the universe allows it.
        // Only activate slots compatible with the session's region filter (structure fidelity).
        // Enforce at most one active slot per (pattern, primaryMuscle) per session — matches
        // CP-SAT uniqueness groups so sparse catalogs are not asked for two rear-delt lifts, etc.
        var cursor = 0
        for (sessionIndex in 0 until sessionCount) {
            val regionFilter = regionFilters[sessionIndex]
            val eligible =
                selectable.filter { slot ->
                    isSlotCompatibleWithRegionFilter(slot, regionFilter)
                }
            val pool = eligible.ifEmpty { selectable }
            val patternMuscleUsed = linkedSetOf<Pair<String, String>>()
            var filled = 0
            var attempts = 0
            val maxAttempts = pool.size * 3
            while (filled < targetSlotsPerSession && attempts < maxAttempts) {
                val slot = pool[cursor % pool.size]
                cursor++
                attempts++
                val patternMuscle = slot.pattern to slot.primaryMuscle
                val isFreshPatternMuscle = patternMuscle !in patternMuscleUsed
                val activated = isFreshPatternMuscle && activeBySession[sessionIndex].add(slot.id)
                if (activated) {
                    patternMuscleUsed += patternMuscle
                    setCounts[sessionIndex to slot.id] = setsPerSlot
                    filled++
                }
            }
            // Guarantee at least one slot so workouts are never empty.
            if (activeBySession[sessionIndex].isEmpty()) {
                val slot = pool[sessionIndex % pool.size]
                activeBySession[sessionIndex].add(slot.id)
                setCounts[sessionIndex to slot.id] = setsPerSlot
            }
        }

        val solution =
            UnifiedSynthesisSolution(
                activeSlotIdsBySession = activeBySession.map { ids -> ids.toSet() },
                setCountBySessionSlot = setCounts,
                weekdayIndexByPosition = weekdayIndices,
                regionFilterBySession = regionFilters,
            )
        return materializeSynthesisResult(universe, solution)
    }

    private fun regionFiltersForSessions(
        weekdays: List<String>,
        sessionCount: Int,
    ): List<SessionRegionFilter> {
        val perSession = regionFiltersBySessionPosition(weekdays)
        if (perSession.isEmpty()) {
            return List(sessionCount) { SessionRegionFilter.Unrestricted }
        }
        return List(sessionCount) { index ->
            val allowed = perSession.getOrElse(index) { perSession[index % perSession.size] }
            when {
                SessionRegionFilter.Unrestricted in allowed -> SessionRegionFilter.Unrestricted
                SessionRegionFilter.LowerOnly in allowed && index % 2 == 0 ->
                    SessionRegionFilter.LowerOnly
                SessionRegionFilter.UpperOnly in allowed -> SessionRegionFilter.UpperOnly
                else -> allowed.first()
            }
        }
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
            allowedKinematicProfiles = allowedKinematicProfiles,
        )

    private companion object {
        const val DEFAULT_SETS = 3
        const val DEFAULT_SLOTS_PER_SESSION = 6
    }
}

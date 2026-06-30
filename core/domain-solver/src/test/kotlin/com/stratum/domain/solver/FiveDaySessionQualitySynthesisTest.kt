@file:Suppress("FunctionNaming")

package com.stratum.domain.solver

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.schema.toDomainProgram
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import com.stratum.domain.program.generation.synthesis.SynthesisSessionDomain
import com.stratum.domain.program.generation.volume.planWeeklyVolume
import com.stratum.domain.solver.VolumeAllocationCoefficients
import com.stratum.domain.program.Workout
import com.stratum.domain.program.generation.reference.MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleVolume
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Locks session-quality invariants for 5-day arm/calf specialization: even set distribution,
 * minimum lift count per session, alternating upper/lower calendar domains, and no back-to-back
 * meaningful muscle repeats.
 */
import com.stratum.core.testing.program.GENERATION_SCENARIOS
import com.stratum.core.testing.program.SPECIALIZATION_PRESETS

class FiveDaySessionQualitySynthesisTest {
    private val specialization =
        mapOf(
            "Calf" to "Specialize",
            "Bicep" to "Specialize",
            "Tricep" to "Specialize",
        )

    @Test
    fun diagnoseAllScenarios() = runTest {
        val pipeline = testProgramGenerationPipeline()
        println("=== DIAGNOSTIC RUN ===")
        for (config in GENERATION_SCENARIOS) {
            val spec = SPECIALIZATION_PRESETS[config.preset].orEmpty()
            val context = parseScenarioContext(scenarioPayload(config.days, spec))
            val normalized = try {
                pipeline.generate(
                    context = context,
                    normalizationRequest = context.toProgramNormalizationRequest(),
                    options = PipelineOptions(skipLiftSwap = true, skipProgramNaming = true),
                )
            } catch (e: Exception) {
                println("Scenario ${config.label} failed to generate: ${e.message}")
                continue
            }
            val draft = normalized.toDraft()
            val program = normalized.toDomainProgram()
            
            // Check adjacent specialized repeats
            val workouts = program.workouts.sortedBy { w ->
                w.weekday?.let { day -> Weekday.entries.indexOf(day) } ?: w.position
            }
            val exposuresByWorkout = draft.workouts.associate { workout ->
                workout.name.orEmpty() to computeWorkoutMuscleVolume(workout, context)
            }
            val repeats = adjacentMeaningfulMuscleRepeats(workouts, exposuresByWorkout, context)
            
            // Check session set counts
            val setCounts = workouts.map { w -> w.workoutLifts.sumOf { l -> l.sets.size } }
            
            if (repeats.isNotEmpty() || setCounts.maxOrNull()!! > setCounts.minOrNull()!! * 2) {
                println("Scenario ${config.label}:")
                if (repeats.isNotEmpty()) {
                    println("  Violations: $repeats")
                }
                println("  Set Counts: $setCounts")
                println("  Schedule: ${workouts.joinToString { "${it.weekday?.name?.take(3)}: ${it.name}" }}")
            }
        }
    }

    @Test
    fun diagnoseSingleRun() = runTest {
        val context = richArmCalfCatalog()
        val pipeline = testProgramGenerationPipeline()
        val normalized = pipeline.generate(
            context = context,
            normalizationRequest = context.toProgramNormalizationRequest(),
            options = PipelineOptions(skipLiftSwap = true, skipProgramNaming = true),
        )
        val draft = normalized.toDraft()
        println("=== DIAGNOSE SINGLE RUN ===")
        for (workout in draft.workouts.sortedBy { it.weekday }) {
            println("Workout ${workout.name} (${workout.weekday}):")
            for (lift in workout.workoutLifts.orEmpty()) {
                val meta = context.liftsById[lift.liftId]!!
                println("  - ${meta.name} (sets=${lift.workoutLiftSets?.size}): pattern=${meta.movementPattern}, primary=${meta.primaryMuscles}, secondary=${meta.secondaryMuscles}")
            }
            println("  Calculated Muscle Volume: ${computeWorkoutMuscleVolume(workout, context)}")
        }
    }

    @Test
    fun fiveDayArmCalfSpecBalancesSessionsAndAvoidsAdjacentMuscleRepeats() = runTest {
        val context = richArmCalfCatalog()
        val synthesizer = CpSatProgramSynthesizer()
        val synthesis = synthesizer.synthesize(planWeeklyVolume(context), context)
        val solverAdjacentRepeats =
            adjacentMeaningfulMuscleRepeatsFromPlans(
                plans = synthesis.plans,
                weekdayIndexByPosition = synthesis.weekdayIndexByPosition,
                context = context,
            )
        assertTrue(
            solverAdjacentRepeats.isEmpty(),
            "Solver must forbid adjacent specialized muscle exposure: $solverAdjacentRepeats\n" +
                "weekdays=${synthesis.weekdayIndexByPosition}\n" +
                scheduleDebug(synthesis),
        )

        val pipeline = testProgramGenerationPipeline(programSynthesisPort = synthesizer)
        val normalized =
            pipeline.generate(
                context = context,
                normalizationRequest = context.toProgramNormalizationRequest(),
                options = PipelineOptions(skipLiftSwap = true, skipProgramNaming = true),
            )
        val draft = normalized.toDraft()
        val program = normalized.toDomainProgram()

        val workouts =
            program.workouts.sortedBy { workout ->
                workout.weekday?.let { day -> Weekday.entries.indexOf(day) } ?: workout.position
            }
        val liftCounts = workouts.map { workout -> workout.workoutLifts.size }
        val setCounts =
            workouts.map { workout ->
                workout.workoutLifts.sumOf { lift -> lift.sets.size }
            }

        val minLifts = liftCounts.minOrNull() ?: 0
        val minSets = setCounts.minOrNull() ?: 0
        val maxSets = setCounts.maxOrNull() ?: 0

        assertTrue(
            minLifts >= 4,
            "Each session needs at least 4 exercises; got liftCounts=$liftCounts",
        )
        assertTrue(
            minSets >= maxSets / 2,
            "Session set counts should stay within 2× of each other: setCounts=$setCounts",
        )
        assertTrue(
            minSets >= 10,
            "No session should be a token 2-lift day: setCounts=$setCounts liftCounts=$liftCounts",
        )

        val exposuresByWorkout =
            draft.workouts.associate { workout ->
                workout.name.orEmpty() to computeWorkoutMuscleVolume(workout, context)
            }
        val adjacentRepeats = adjacentMeaningfulMuscleRepeats(workouts, exposuresByWorkout, context)
        assertTrue(
            adjacentRepeats.isEmpty(),
            "Avoid back-to-back meaningful muscle exposure: $adjacentRepeats",
        )

        val domains = inferDomainsFromWorkouts(draft, context)
        val upperUpperAdjacent =
            workouts.zip(domains).windowed(2).count { window ->
                window[0].second == SynthesisSessionDomain.Upper &&
                    window[1].second == SynthesisSessionDomain.Upper
            }
        assertTrue(
            upperUpperAdjacent == 0,
            "Consecutive upper-upper calendar days are forbidden; domains=$domains",
        )
    }

    private fun scheduleDebug(
        synthesis: com.stratum.domain.program.generation.synthesis.ProgramSynthesisResult,
    ): String =
        synthesis.plans
            .sortedBy { plan -> synthesis.weekdayIndexByPosition[plan.position] }
            .joinToString(" | ") { plan ->
                val day = Weekday.entries[synthesis.weekdayIndexByPosition[plan.position]]
                val credits = planMuscleCredits(plan)
                val calf = credits["Calf"] ?: 0.0
                val bicep = credits["Bicep"] ?: 0.0
                "${day.name.take(3)} p${plan.position} calf=$calf bicep=$bicep sets=${plan.filledSlots.sumOf { it.setCount }}"
            }

    private fun adjacentMeaningfulMuscleRepeatsFromPlans(
        plans: List<com.stratum.domain.program.generation.prescription.SessionSlotPlan>,
        weekdayIndexByPosition: List<Int>,
        context: ProgramGenerationContext,
    ): List<String> {
        val specialized =
            context.specialization
                .filterValues { level -> level == "Specialize" }
                .keys
        val ordered =
            plans.sortedBy { plan -> weekdayIndexByPosition[plan.position] }
        val issues = mutableListOf<String>()
        for (index in 1 until ordered.size) {
            val prevDay = Weekday.entries[weekdayIndexByPosition[ordered[index - 1].position]]
            val currDay = Weekday.entries[weekdayIndexByPosition[ordered[index].position]]
            val calendarGap = currDay.ordinal - prevDay.ordinal
            if (calendarGap != 1) continue
            val prev = planMuscleCredits(ordered[index - 1])
            val curr = planMuscleCredits(ordered[index])
            for (muscle in prev.keys.intersect(curr.keys)) {
                if (muscle !in specialized) continue
                val prevDose = prev[muscle] ?: 0.0
                val currDose = curr[muscle] ?: 0.0
                if (
                    prevDose >= MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE &&
                    currDose >= MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE
                ) {
                    issues +=
                        "Session ${ordered[index - 1].position}→${ordered[index].position}: " +
                        "$muscle ($prevDose, $currDose)"
                }
            }
        }
        return issues
    }

    private fun planMuscleCredits(
        plan: com.stratum.domain.program.generation.prescription.SessionSlotPlan,
    ): Map<String, Double> {
        val totals = mutableMapOf<String, Double>()
        for (slot in plan.filledSlots) {
            if (slot.setCount <= 0) continue
            val muscles = linkedSetOf(slot.primaryMuscle)
            com.stratum.domain.program.generation.reference.PATTERN_SECONDARY_MUSCLE_CREDITS[slot.pattern]
                ?.forEach { muscles += it.muscle }
            for (muscle in muscles) {
                val coeff = VolumeAllocationCoefficients.coeff(slot, muscle)
                if (coeff <= 0.0) continue
                totals[muscle] = (totals[muscle] ?: 0.0) + slot.setCount * coeff
            }
        }
        return totals
    }

    private fun adjacentMeaningfulMuscleRepeats(
        workouts: List<Workout>,
        exposuresByWorkout: Map<String, Map<String, Double>>,
        context: ProgramGenerationContext,
    ): List<String> {
        val specialized =
            context.specialization
                .filterValues { level -> level == "Specialize" }
                .keys
        val issues = mutableListOf<String>()
        for (index in 1 until workouts.size) {
            val prevDay = workouts[index - 1].weekday ?: continue
            val currDay = workouts[index].weekday ?: continue
            val calendarGap =
                Weekday.entries.indexOf(currDay) - Weekday.entries.indexOf(prevDay)
            if (calendarGap != 1) continue
            val prev = exposuresByWorkout[workouts[index - 1].name].orEmpty()
            val curr = exposuresByWorkout[workouts[index].name].orEmpty()
            for (muscle in prev.keys.intersect(curr.keys)) {
                if (muscle !in specialized) continue
                val prevDose = prev[muscle] ?: 0.0
                val currDose = curr[muscle] ?: 0.0
                if (
                    prevDose >= MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE &&
                    currDose >= MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE
                ) {
                    issues +=
                        "${workouts[index - 1].name}→${workouts[index].name}: $muscle ($prevDose, $currDose)"
                }
            }
        }
        return issues
    }

    private fun inferDomainsFromWorkouts(
        draft: com.stratum.domain.program.generation.draft.ProgramDraft,
        context: ProgramGenerationContext,
    ): List<SynthesisSessionDomain> =
        draft.workouts.map { workout ->
            val muscles =
                computeWorkoutMuscleVolume(workout, context).keys
            val lower =
                muscles.any { muscle -> muscle in setOf("Quad", "Hamstring", "Glute", "Calf", "LowerBack") }
            val upper =
                muscles.any { muscle -> muscle in setOf("Chest", "Back", "Bicep", "Tricep", "AnteriorDeltoid", "LateralDeltoid", "PosteriorDeltoid") }
            when {
                lower && !upper -> SynthesisSessionDomain.Lower
                upper && !lower -> SynthesisSessionDomain.Upper
                else -> SynthesisSessionDomain.Full
            }
        }

    private fun richArmCalfCatalog(): ProgramGenerationContext {
        val base = parseScenarioContext(scenarioPayload(5, specialization, weekdayLayout = "mtwfs"))
        val extraLifts =
            listOf(
                lift("hammer-curl", "Hammer Curl", "ElbowFlexion", listOf("Bicep")),
                lift("preacher-curl", "Preacher Curl", "ElbowFlexion", listOf("Bicep")),
                lift("rope-tricep", "Rope Tricep Extension", "ElbowExtension", listOf("Tricep")),
                lift("skull-crusher", "Skull Crusher", "ElbowExtension", listOf("Tricep")),
                lift("standing-calf", "Standing Calf Raise", "PlantarFlexion", listOf("Calf")),
                lift("donkey-calf", "Donkey Calf Raise", "PlantarFlexion", listOf("Calf")),
                lift("lat-pulldown", "Cable Lat Pulldown", "VerticalPull", listOf("Back"), listOf("Bicep")),
                lift("pull-down-neutral", "Neutral Grip Lat Pulldown", "VerticalPull", listOf("Back"), listOf("Bicep")),
                lift("close-grip-bench", "Close Grip Bench Press", "HorizontalPress", listOf("Tricep"), listOf("Chest")),
                lift("chin-up-bicep", "Bicep Dominant Chin Up", "VerticalPull", listOf("Bicep"), listOf("Back")),
            )
        val liftsById = base.liftsById.toMutableMap()
        val enabled = base.enabledLiftIds.toMutableSet()
        for (extra in extraLifts) {
            liftsById[extra.id] = extra
            enabled += extra.id
        }
        return base.copy(
            liftsById = liftsById,
            enabledLiftIds = enabled,
            movementPatternByLiftId = liftsById.mapValues { (_, value) -> value.movementPattern },
        )
    }

    private fun lift(
        id: String,
        name: String,
        pattern: String,
        primary: List<String>,
        secondary: List<String> = emptyList(),
    ): LiftContext =
        LiftContext(
            id = id,
            name = name,
            movementPattern = pattern,
            movementPatterns = listOf(pattern),
            primaryMuscles = primary,
            secondaryMuscles = secondary,
            proven = true,
            mainstream = true,
        )
}

@file:Suppress("FunctionNaming")

package com.stratum.domain.solver

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.schema.toDomainProgram
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import com.stratum.domain.program.generation.volume.planWeeklyVolume
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Locks session-quality invariants for 5-day arm/calf specialization: even set distribution,
 * minimum lift count per session, and class-aware recovery spacing (24h lane for isolation
 * low-dose; 48h+ for moderate isolation and compounds).
 */

class FiveDaySessionQualitySynthesisTest {
    private val specialization =
        mapOf(
            "Calf" to "Specialize",
            "Bicep" to "Specialize",
            "Tricep" to "Specialize",
        )

    @Test
    fun fiveDayArmCalfSpecBalancesSessionsAndAvoidsAdjacentMuscleRepeats() = runBlocking {
        val context = richArmCalfCatalog()
        val synthesizer = CpSatProgramSynthesizer()
        val synthesis = synthesizer.synthesize(planWeeklyVolume(context), context)
        val specializedMuscles =
            context.specialization.filterValues { it == SpecializationLevel.Specialize }.keys.map { it.name }.toSet()
        val solverAdjacentRepeats =
            recoveryGapViolationsFromPlans(
                plans = synthesis.plans,
                weekdayIndexByPosition = synthesis.weekdayIndexByPosition,
                context = context,
                muscleFilter = { muscle -> muscle in specializedMuscles },
                calendarGapFilter = 1,
            )
        assertTrue(
            solverAdjacentRepeats.isEmpty(),
            "Solver must forbid adjacent specialized muscle exposure: $solverAdjacentRepeats\n" +
                "weekdays=${synthesis.weekdayIndexByPosition}\n" +
                scheduleDebug(synthesis, context),
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
            minLifts >= 3,
            "Each session needs at least 3 exercises; got liftCounts=$liftCounts",
        )
        assertTrue(
            minSets >= maxSets / 2,
            "Session set counts should stay within 2× of each other: setCounts=$setCounts",
        )
        assertTrue(
            minSets >= 10,
            "No session should be a token 2-lift day: setCounts=$setCounts liftCounts=$liftCounts",
        )

        val pipelineAdjacentRepeats =
            recoveryGapViolationsFromDraftWorkouts(
                workouts = draft.workouts,
                context = context,
                muscleFilter = { muscle -> muscle in specializedMuscles },
                calendarGapFilter = 1,
            )
        assertTrue(
            pipelineAdjacentRepeats.isEmpty(),
            "Pipeline metadata must match class-aware adjacent recovery: $pipelineAdjacentRepeats\n" +
                scheduleDebug(synthesis, context),
        )
    }

    private fun scheduleDebug(
        synthesis: com.stratum.domain.program.generation.synthesis.ProgramSynthesisResult,
        context: ProgramGenerationContext,
    ): String =
        synthesis.plans
            .sortedBy { plan -> synthesis.weekdayIndexByPosition[plan.position] }
            .joinToString(" | ") { plan ->
                val day = Weekday.entries[synthesis.weekdayIndexByPosition[plan.position]]
                val stress = planMuscleSessionStress(plan, context)
                val calf = stress["Calf"]?.fractionalSets ?: 0.0
                val bicep = stress["Bicep"]?.fractionalSets ?: 0.0
                "${day.name.take(3)} p${plan.position} calf=$calf bicep=$bicep sets=${plan.filledSlots.sumOf { it.setCount }}"
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
            movementPattern = MovementPattern.valueOf(pattern),
            movementPatterns = setOf(MovementPattern.valueOf(pattern)),
            primaryMuscles = primary.map { MuscleGroup.valueOf(it) }.toSet(),
            secondaryMuscles = secondary.map { MuscleGroup.valueOf(it) }.toSet(),
            proven = true,
            mainstream = true,
        )
}

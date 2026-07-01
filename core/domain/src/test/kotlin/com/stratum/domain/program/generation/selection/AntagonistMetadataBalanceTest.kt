package com.stratum.domain.program.generation.selection

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleVolume
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AntagonistMetadataBalanceTest {
    @Test
    fun `penalizes quad-heavy lift when ham trails quad on metadata volume`() {
        val context = maintenanceContext()
        val running = mutableMapOf("Quad" to 10.0, "Hamstring" to 5.0)
        val legExtension =
            lift(
                id = "leg-ext",
                pattern = "KneeExtension",
                primary = listOf("Quad"),
            )
        val legCurl =
            lift(
                id = "leg-curl",
                pattern = "KneeFlexion",
                primary = listOf("Hamstring"),
            )

        val quadHeavy = AntagonistMetadataBalance.balanceBonus(legExtension, setCount = 4, context, running)
        val hamBoost = AntagonistMetadataBalance.balanceBonus(legCurl, setCount = 4, context, running)

        assertTrue(quadHeavy < hamBoost, "quad=$quadHeavy ham=$hamBoost")
    }

    @Test
    fun calfTricepFiveDaySixtyMinuteBalancesQuadAndHamMetadataVolume() = runBlocking {
        val specialization = mapOf("Calf" to "Specialize", "Tricep" to "Specialize")
        val context =
            parseScenarioContext(
                scenarioPayload(
                    days = 5,
                    specialization = specialization,
                    weekdayLayout = "mtwfs",
                    sessionLengthMinutes = 60,
                ),
            )
        val pipeline = testProgramGenerationPipeline()
        val normalized =
            pipeline.generate(
                context = context,
                normalizationRequest = context.toProgramNormalizationRequest(),
                options = PipelineOptions(skipLiftSwap = true, skipProgramNaming = true),
            )
        val draft = normalized.toDraft()
        val weekly = weeklyMetadataVolume(draft, context)
        val exerciseCounts = exerciseCountsByMuscle(draft, context)
        val quad = weekly.getValue("Quad")
        val ham = weekly.getValue("Hamstring")

        assertTrue(
            ham + 1.0 >= quad,
            "Ham should meet or exceed quad (metadata): quad=$quad ham=$ham weekly=$weekly",
        )
        assertTrue(
            kotlin.math.abs(exerciseCounts.getValue("Quad") - exerciseCounts.getValue("Hamstring")) <= 2,
            "Exercise variety should stay close: counts=$exerciseCounts",
        )
    }

    private fun weeklyMetadataVolume(
        draft: ProgramDraft,
        context: ProgramGenerationContext,
    ): Map<String, Double> {
        val totals = mutableMapOf<String, Double>()
        for (workout in draft.workouts) {
            for ((muscle, dose) in computeWorkoutMuscleVolume(workout, context)) {
                totals[muscle] = (totals[muscle] ?: 0.0) + dose
            }
        }
        return totals
    }

    private fun exerciseCountsByMuscle(
        draft: ProgramDraft,
        context: ProgramGenerationContext,
    ): Map<String, Int> {
        val exercisesByMuscle = mutableMapOf<String, MutableSet<String>>()
        for (workout in draft.workouts) {
            for (workoutLift in workout.workoutLifts.orEmpty()) {
                val liftId = workoutLift.liftId.orEmpty()
                if ((workoutLift.workoutLiftSets?.size ?: 0) == 0) continue
                val meta = context.liftsById[liftId] ?: continue
                val muscles = meta.primaryMuscles + meta.secondaryMuscles.filter { it !in meta.primaryMuscles }
                for (muscle in muscles) {
                    exercisesByMuscle.getOrPut(muscle) { mutableSetOf() }.add(liftId)
                }
            }
        }
        return exercisesByMuscle.mapValues { (_, ids) -> ids.size }
    }

    private fun maintenanceContext(): ProgramGenerationContext =
        parseScenarioContext(
            scenarioPayload(
                days = 4,
                specialization = emptyMap(),
            ),
        )

    private fun lift(
        id: String,
        pattern: String,
        primary: List<String>,
        secondary: List<String> = emptyList(),
    ): LiftContext =
        LiftContext(
            id = id,
            name = id,
            movementPattern = pattern,
            movementPatterns = listOf(pattern),
            primaryMuscles = primary,
            secondaryMuscles = secondary,
            proven = true,
            mainstream = true,
        )
}

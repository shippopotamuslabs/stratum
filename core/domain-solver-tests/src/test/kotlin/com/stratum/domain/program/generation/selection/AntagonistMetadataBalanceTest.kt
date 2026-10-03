package com.stratum.domain.program.generation.selection

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleVolume
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.stratum.domain.solver.CpSatProgramSynthesizer

class AntagonistMetadataBalanceTest {
    @Test
    fun `penalizes quad-heavy lift when ham trails quad on metadata volume`() {
        val context = maintenanceContext()
        val running = mutableMapOf(MuscleGroup.Quad to 10.0, MuscleGroup.Hamstring to 5.0)
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
    fun `penalizes biceps-heavy lift when triceps trails on metadata volume`() {
        val context = maintenanceContext()
        val running = mutableMapOf(MuscleGroup.Bicep to 10.0, MuscleGroup.Tricep to 5.0)
        val curl =
            lift(
                id = "curl",
                pattern = "ElbowFlexion",
                primary = listOf("Bicep"),
            )
        val pushdown =
            lift(
                id = "pushdown",
                pattern = "ElbowExtension",
                primary = listOf("Tricep"),
            )

        val bicepHeavy = AntagonistMetadataBalance.balanceBonus(curl, setCount = 4, context, running)
        val tricepBoost = AntagonistMetadataBalance.balanceBonus(pushdown, setCount = 4, context, running)

        assertTrue(bicepHeavy < tricepBoost, "biceps=$bicepHeavy triceps=$tricepBoost")
    }

    @Test
    fun `symmetric pair has no directional preference when balanced`() {
        val context = maintenanceContext()
        val running = mutableMapOf(MuscleGroup.Bicep to 5.0, MuscleGroup.Tricep to 5.0)
        val curl =
            lift(
                id = "curl",
                pattern = "ElbowFlexion",
                primary = listOf("Bicep"),
            )
        val pushdown =
            lift(
                id = "pushdown",
                pattern = "ElbowExtension",
                primary = listOf("Tricep"),
            )

        val bicepScore = AntagonistMetadataBalance.balanceBonus(curl, setCount = 4, context, running)
        val tricepScore = AntagonistMetadataBalance.balanceBonus(pushdown, setCount = 4, context, running)

        assertEquals(
            bicepScore,
            tricepScore,
            1e-9,
            "Symmetric pair should not favor one side: biceps=$bicepScore triceps=$tricepScore",
        )
    }

    @Test
    fun `penalizes triceps-heavy lift when biceps trails on metadata volume`() {
        val context = maintenanceContext()
        val running = mutableMapOf(MuscleGroup.Bicep to 5.0, MuscleGroup.Tricep to 10.0)
        val curl =
            lift(
                id = "curl",
                pattern = "ElbowFlexion",
                primary = listOf("Bicep"),
            )
        val pushdown =
            lift(
                id = "pushdown",
                pattern = "ElbowExtension",
                primary = listOf("Tricep"),
            )

        val bicepBoost = AntagonistMetadataBalance.balanceBonus(curl, setCount = 4, context, running)
        val tricepHeavy = AntagonistMetadataBalance.balanceBonus(pushdown, setCount = 4, context, running)

        assertTrue(bicepBoost > tricepHeavy, "biceps=$bicepBoost triceps=$tricepHeavy")
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
        val pipeline = testProgramGenerationPipeline(programSynthesisPort = CpSatProgramSynthesizer())
        val normalized =
            pipeline.generate(
                context = context,
                normalizationRequest = context.toProgramNormalizationRequest(),
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
                totals[muscle.name] = (totals[muscle.name] ?: 0.0) + dose
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
                    exercisesByMuscle.getOrPut(muscle.name) { mutableSetOf() }.add(liftId)
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
            movementPattern = runCatching { MovementPattern.valueOf(pattern) }.getOrNull(),
            movementPatterns = setOfNotNull(runCatching { MovementPattern.valueOf(pattern) }.getOrNull()),
            primaryMuscles = primary.mapNotNull { runCatching { MuscleGroup.valueOf(it) }.getOrNull() }.toSet(),
            secondaryMuscles = secondary.mapNotNull { runCatching { MuscleGroup.valueOf(it) }.getOrNull() }.toSet(),
            proven = true,
            mainstream = true,
        )
}

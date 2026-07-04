package com.stratum.domain.program.generation.selection

import com.stratum.core.testing.program.lowerDominantWorkouts
import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.MuscleLengthBias
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LiftVarietyIntegrationTest {

    @Test
    fun sixDayCycleSpreadsCalfAndSquatLiftsAcrossLowerDays() = runBlocking {
        val context = richLowerBodyCatalog()
        val program =
            testProgramGenerationPipeline().generate(
                context = context,
                normalizationRequest = context.toProgramNormalizationRequest(),
                options = PipelineOptions(skipLiftSwap = true),
            )
        val draft = program.toDraft()
        val lowerLiftIds =
            lowerDominantWorkouts(draft, context)
                .flatMap { workout -> workout.workoutLifts.orEmpty().mapNotNull { it.liftId } }
        val calfLifts =
            lowerLiftIds.filter { liftId ->
                val meta = context.liftsById[liftId] ?: return@filter false
                meta.movementPattern == MovementPattern.PlantarFlexion
            }
        val squatLifts =
            lowerLiftIds.filter { liftId ->
                val meta = context.liftsById[liftId] ?: return@filter false
                meta.movementPattern == MovementPattern.SquatPattern
            }
        assertTrue(
            calfLifts.toSet().size >= 2,
            "Expected ≥2 distinct calf lifts across lower days, got $calfLifts",
        )
        assertTrue(
            squatLifts.toSet().size >= 1,
            "Expected at least one squat-pattern lift across lower days, got $squatLifts",
        )
    }

    private fun richLowerBodyCatalog(): ProgramGenerationContext {
        val base = parseScenarioContext(
            scenarioPayload(
                days = 6,
                specialization = mapOf("Calf" to "Specialize", "Bicep" to "Specialize", "Tricep" to "Specialize"),
            ),
        )
        val extraLifts =
            listOf(
                lift("donkey-calf", "Donkey Calf Raise", "PlantarFlexion", listOf("Calf")),
                lift("seated-calf-machine", "Seated Calf Raise Machine", "PlantarFlexion", listOf("Calf")),
                lift("standing-calf", "Standing Calf Raise", "PlantarFlexion", listOf("Calf")),
                lift("front-squat-bb", "Barbell Front Squat", "SquatPattern", listOf("Quad"), listOf("Glute")),
                lift("back-squat", "Barbell Back Squat", "SquatPattern", listOf("Quad"), listOf("Glute")),
                lift("hack-squat", "Hack Squat", "SquatPattern", listOf("Quad")),
            )
        val liftsById = base.liftsById.toMutableMap()
        val enabled = base.enabledLiftIds.toMutableSet()
        for (lift in extraLifts) {
            liftsById[lift.id] = lift
            enabled += lift.id
        }
        return base.copy(
            liftsById = liftsById,
            enabledLiftIds = enabled,
            movementPatternByLiftId = liftsById.mapValues { (_, lift) -> lift.movementPattern },
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
            movementPattern = runCatching { MovementPattern.valueOf(pattern) }.getOrNull(),
            movementPatterns = setOfNotNull(runCatching { MovementPattern.valueOf(pattern) }.getOrNull()),
            primaryMuscles = primary.mapNotNull { runCatching { MuscleGroup.valueOf(it) }.getOrNull() }.toSet(),
            secondaryMuscles = secondary.mapNotNull { runCatching { MuscleGroup.valueOf(it) }.getOrNull() }.toSet(),
            proven = true,
            mainstream = true,
        )
}

package com.stratum.domain.program.generation.prescription

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.domain.lift.MovementPattern
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.program.generation.draft.ProgramWorkoutDraft
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CalfSpecializationLowerDayTest {

    @Test
    fun calfAndArmSpecLowerDaysRetainSquatAndHingeVolume() = runBlocking {
        val specialization =
            mapOf(
                "Calf" to "Specialize",
                "Bicep" to "Specialize",
                "Tricep" to "Specialize",
            )
        val context = parseScenarioContext(scenarioPayload(4, specialization))
        assertLowerWorkoutsHaveLegAnchors(context, specialization)
    }

    @Test
    fun sixDayCalfAndArmSpecLowerDaysRetainSquatAndHingeVolume() = runBlocking {
        val specialization =
            mapOf(
                "Calf" to "Specialize",
                "Bicep" to "Specialize",
                "Tricep" to "Specialize",
            )
        val context = parseScenarioContext(scenarioPayload(6, specialization))
        assertLowerWorkoutsHaveLegAnchors(context, specialization)
    }

    @Test
    fun thirtyMinuteSessionCalfSpecLowerDaysRetainLegAnchors() = runBlocking {
        val specialization =
            mapOf(
                "Calf" to "Specialize",
                "Bicep" to "Specialize",
            )
        val context = parseScenarioContext(scenarioPayload(6, specialization, sessionLengthMinutes = 30))
        assertLowerWorkoutsHaveLegAnchors(context, specialization)
    }

    private suspend fun assertLowerWorkoutsHaveLegAnchors(
        context: ProgramGenerationContext,
        specialization: Map<String, String>,
    ) {
        val program = testProgramGenerationPipeline().generate(
            context = context,
            normalizationRequest = context.toProgramNormalizationRequest(),
            options = PipelineOptions(skipLiftSwap = true),
        )
        val draft = program.toDraft()
        for (workout in draft.workouts) {
            val squatSets = primarySetsForPattern(workout, context, "SquatPattern")
            val hingeSets = primarySetsForPattern(workout, context, "HipHinge")
            if (squatSets == 0 && hingeSets == 0) continue
            if (squatSets > 0) {
                assertTrue(
                    squatSets >= 2,
                    "${workout.name} squat-pattern sets=$squatSets (expected ≥2) spec=$specialization",
                )
            }
            if (hingeSets > 0) {
                assertTrue(
                    hingeSets >= 2,
                    "${workout.name} hinge-pattern sets=$hingeSets (expected ≥2) spec=$specialization",
                )
            }
        }
    }

    private fun primarySetsForPattern(
        workout: ProgramWorkoutDraft,
        context: ProgramGenerationContext,
        pattern: String,
    ): Int {
        val resolved = runCatching { MovementPattern.valueOf(pattern) }.getOrNull()
        var total = 0
        for (lift in workout.workoutLifts.orEmpty()) {
            val meta = context.liftsById[lift.liftId.orEmpty()] ?: continue
            if (resolved != null && meta.movementPattern != resolved && resolved !in meta.movementPatterns) continue
            total += lift.workoutLiftSets?.size ?: 0
        }
        return total
    }
}

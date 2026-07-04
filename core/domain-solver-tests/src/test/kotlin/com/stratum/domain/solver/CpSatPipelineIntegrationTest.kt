@file:Suppress("FunctionNaming", "MaxLineLength")

package com.stratum.domain.solver

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleVolume
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * End-to-end pipeline test wiring [CpSatProgramSynthesizer] through the full
 * `ProgramGenerationPipeline`. Verifies the solver integrates cleanly with
 * lift selection, assembly, and integration/repair — and that the dual-arm
 * balance property survives the downstream stages.
 */
class CpSatPipelineIntegrationTest {

    @Test
    @DisplayName("full pipeline with CP-SAT allocator balances dual arm weekly volume")
    fun fullPipelineWithCpSatBalancesDualArm() = runBlocking {
        val context = parseScenarioContext(
            scenarioPayload(4, mapOf("Bicep" to "Specialize", "Tricep" to "Specialize")),
        )
        val pipeline = testProgramGenerationPipeline(
            programSynthesisPort = CpSatProgramSynthesizer(),
        )
        val program = pipeline.generate(
            context = context,
            normalizationRequest = context.toProgramNormalizationRequest(),
            options = PipelineOptions(skipLiftSwap = true, skipProgramNaming = true),
        )
        val draft = program.toDraft()

        val bicepWeekly = weeklyMuscleVolume(draft, context, "Bicep")
        val tricepWeekly = weeklyMuscleVolume(draft, context, "Tricep")

        // Phase 2 wiring smoke: the solver-driven pipeline produces a complete
        // program with arm volume in a reasonable range. Exact balance is
        // verified in CombinatorialSpecializationRegressionTest; downstream
        // repair/trim can shift a set.
        assertTrue(bicepWeekly >= 8.0, "Bicep weekly volume too low: $bicepWeekly")
        assertTrue(tricepWeekly >= 8.0, "Tricep weekly volume too low: $tricepWeekly")
        assertEquals(
            tricepWeekly,
            bicepWeekly,
            4.0,
            "Bicep ($bicepWeekly) and Tricep ($tricepWeekly) should stay close after full pipeline",
        )
    }

    private fun weeklyMuscleVolume(
        draft: com.stratum.domain.program.generation.draft.ProgramDraft,
        context: com.stratum.domain.program.generation.context.ProgramGenerationContext,
        muscle: String,
    ): Double {
        val muscleGroup = runCatching { com.stratum.domain.onboarding.MuscleGroup.valueOf(muscle) }.getOrNull() ?: return 0.0
        var total = 0.0
        for (workout in draft.workouts) {
            total += computeWorkoutMuscleVolume(workout, context)[muscleGroup] ?: 0.0
        }
        return total
    }
}
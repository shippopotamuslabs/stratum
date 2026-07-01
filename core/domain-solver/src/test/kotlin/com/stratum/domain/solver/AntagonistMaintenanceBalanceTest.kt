@file:Suppress("FunctionNaming")

package com.stratum.domain.solver

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleVolume
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * When chest/back and quad/ham share the same maintenance tier, weekly volume should stay
 * close and favor the joint-safer direction (back ≥ chest, ham ≥ quad) when imbalanced.
 */
class AntagonistMaintenanceBalanceTest {
    @Test
    fun fiveDayArmCalfSpecBalancesMaintenanceAntagonistPairs() = runBlocking {
        val specialization =
            mapOf(
                "Calf" to "Specialize",
                "Bicep" to "Specialize",
                "Tricep" to "Specialize",
            )
        val context =
            parseScenarioContext(
                scenarioPayload(
                    days = 5,
                    specialization = specialization,
                    weekdayLayout = "mtwfs",
                ),
            )
        val pipeline = testProgramGenerationPipeline(programSynthesisPort = CpSatProgramSynthesizer())
        val normalized =
            pipeline.generate(
                context = context,
                normalizationRequest = context.toProgramNormalizationRequest(),
                options = PipelineOptions(skipLiftSwap = true, skipProgramNaming = true),
            )
        val draft = normalized.toDraft()
        val weekly = weeklyMuscleVolume(draft, context)
        val chest = weekly["Chest"] ?: 0.0
        val back = weekly["Back"] ?: 0.0
        val quad = weekly["Quad"] ?: 0.0
        val ham = weekly["Hamstring"] ?: 0.0

        assertTrue(
            kotlin.math.abs(chest - back) <= 6.0,
            "Chest/back should stay within 6 sets when both are maintenance: chest=$chest back=$back",
        )
        assertTrue(
            back + 0.5 >= chest,
            "Prefer back ≥ chest when imbalanced (joint safety): chest=$chest back=$back",
        )
        assertTrue(
            kotlin.math.abs(quad - ham) <= 6.0,
            "Quad/ham should stay within 6 sets when both are maintenance: quad=$quad ham=$ham",
        )
        assertTrue(
            ham + 0.5 >= quad,
            "Prefer ham ≥ quad when imbalanced (joint safety): quad=$quad ham=$ham",
        )
    }

    private fun weeklyMuscleVolume(
        draft: com.stratum.domain.program.generation.draft.ProgramDraft,
        context: com.stratum.domain.program.generation.context.ProgramGenerationContext,
    ): Map<String, Double> {
        val totals = mutableMapOf<String, Double>()
        for (workout in draft.workouts) {
            for ((muscle, dose) in computeWorkoutMuscleVolume(workout, context)) {
                totals[muscle] = (totals[muscle] ?: 0.0) + dose
            }
        }
        return totals
    }
}

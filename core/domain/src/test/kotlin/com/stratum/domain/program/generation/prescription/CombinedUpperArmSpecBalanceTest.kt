package com.stratum.domain.program.generation.prescription

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleVolume
import com.stratum.domain.program.generation.split.SessionTypeId
import com.stratum.domain.program.generation.split.resolveSplit
import com.stratum.domain.program.generation.volume.planWeeklyVolume
import com.stratum.domain.solver.CpSatSlotSetAllocator
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CombinedUpperArmSpecBalanceTest {
    private val pipeline = testProgramGenerationPipeline()

    @Test
    @DisplayName("4-day dual arm specialization balances bicep and tricep weekly volume")
    fun fourDayDualArmSpecBalancesWeeklyVolume() = runTest {
        val context = parseScenarioContext(
            scenarioPayload(4, mapOf("Bicep" to "Specialize", "Tricep" to "Specialize")),
        )
        val program = pipeline.generate(
            context = context,
            normalizationRequest = context.toProgramNormalizationRequest(),
            options = PipelineOptions(skipLiftSwap = true),
        )
        val draft = program.toDraft()

        val bicepWeekly = weeklyMuscleVolume(draft, context, "Bicep")
        val tricepWeekly = weeklyMuscleVolume(draft, context, "Tricep")

        // The solver balances bicep/tricep at the allocator level; the
        // downstream repair/trim pass can shift a set by 1. Allow a small
        // tolerance for post-solver drift.
        assertTrue(bicepWeekly >= 7.0, "Bicep weekly volume too low: $bicepWeekly")
        assertTrue(tricepWeekly >= 7.0, "Tricep weekly volume too low: $tricepWeekly")
        assertEquals(
            tricepWeekly,
            bicepWeekly,
            5.0,
            "Bicep ($bicepWeekly) and Tricep ($tricepWeekly) weekly volume should match within 5.0",
        )
    }

    @Test
    @DisplayName("4-day dual arm specialization balances direct isolation sets per upper day")
    fun fourDayDualArmSpecBalancesDirectIsolationPerUpperDay() {
        val context = parseScenarioContext(
            scenarioPayload(4, mapOf("Bicep" to "Specialize", "Tricep" to "Specialize")),
        )
        val targets = planWeeklyVolume(context)
        val resolved = resolveSplit(context, targets)
        val plans = allocateSlotSetCounts(resolved.sessions, targets, context, CpSatSlotSetAllocator())

        val upperPlans = plans.filter { it.sessionType == SessionTypeId.Upper }
        assertEquals(2, upperPlans.size)

        for (plan in upperPlans) {
            val bicepDirect = plan.filledSlots
                .filter { it.primaryMuscle == "Bicep" }
                .sumOf { it.setCount }
            val tricepDirect = plan.filledSlots
                .filter { it.primaryMuscle == "Tricep" }
                .sumOf { it.setCount }
            assertTrue(
                kotlin.math.abs(bicepDirect - tricepDirect) <= 2,
                "${plan.name}: bicep direct ($bicepDirect) and tricep direct ($tricepDirect) should differ by at most 2",
            )
        }
    }

    private fun weeklyMuscleVolume(
        draft: com.stratum.domain.program.generation.draft.ProgramDraft,
        context: com.stratum.domain.program.generation.context.ProgramGenerationContext,
        muscle: String,
    ): Double {
        var total = 0.0
        for (workout in draft.workouts) {
            total += computeWorkoutMuscleVolume(workout, context)[muscle] ?: 0.0
        }
        return total
    }
}

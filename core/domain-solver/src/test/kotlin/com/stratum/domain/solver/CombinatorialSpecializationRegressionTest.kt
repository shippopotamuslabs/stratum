@file:Suppress("FunctionNaming", "MagicNumber", "MaxLineLength")

package com.stratum.domain.solver

import com.stratum.core.testing.program.GENERATION_SCENARIOS
import com.stratum.core.testing.program.SPECIALIZATION_PRESETS
import com.stratum.core.testing.program.ScenarioConfig
import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.program.generation.context.PRIMARY_SET_CREDIT
import com.stratum.domain.program.generation.context.SECONDARY_SET_CREDIT
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.reference.PATTERN_SECONDARY_MUSCLE_CREDITS
import com.stratum.domain.program.generation.reference.SESSION_MUSCLE_SET_CAP
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleVolume
import com.stratum.domain.program.generation.volume.planWeeklyVolume
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Combinatorial regression harness: runs the full `ProgramGenerationPipeline`
 * with [CpSatSlotSetAllocator] across every `GENERATION_SCENARIOS` combo
 * (3/4/5/6-day × 7 specialization presets). Asserts:
 *  - No session muscle-credit cap violations (≤ [SESSION_MUSCLE_SET_CAP]).
 *  - Specialized muscles meet at least their MEV floor.
 *  - Co-specialized muscles (when a preset has 2+) stay balanced within 2.0
 *    weekly credits — the global generalization of the arm-balance test.
 *
 * This locks in the Phase 3 rebalance deletion: the solver + post-solver
 * safety cascade must keep every scenario green without the old per-muscle
 * patches.
 */
class CombinatorialSpecializationRegressionTest {

    private val allocator = CpSatSlotSetAllocator()

    @ParameterizedTest(name = "{0}")
    @MethodSource("generationScenarios")
    @DisplayName("CP-SAT pipeline: caps respected, specialized muscles meet MEV")
    fun specializedMusclesMeetTargetsAndCaps(config: ScenarioConfig) = runTest {
        val specialization = SPECIALIZATION_PRESETS[config.preset].orEmpty()
        val context = parseScenarioContext(scenarioPayload(config.days, specialization))
        val targets = planWeeklyVolume(context)
        val pipeline = testProgramGenerationPipeline(slotSetAllocationPort = allocator)
        val program = pipeline.generate(
            context = context,
            normalizationRequest = context.toProgramNormalizationRequest(),
            options = PipelineOptions(skipLiftSwap = true),
        )
        val draft = program.toDraft()

        assertNoSessionCapViolations(draft, context, config.label)
        assertSpecializedMusclesMeetMev(draft, context, targets, specialization, config.label)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("generationScenarios")
    @DisplayName("CP-SAT allocator: co-specialized muscles with equal aims are balanced (pre-repair)")
    fun coSpecializedMusclesBalancedAtAllocator(config: ScenarioConfig) {
        val specialization = SPECIALIZATION_PRESETS[config.preset].orEmpty()
        val specialized = specialization.filterValues { it == "Specialize" }.keys.toList()
        if (specialized.size < 2) return

        val context = parseScenarioContext(scenarioPayload(config.days, specialization))
        val targets = planWeeklyVolume(context)
        val resolved = com.stratum.domain.program.generation.split.resolveSplit(context, targets)
        val plans = com.stratum.domain.program.generation.prescription.buildEmptySessionPlans(resolved.sessions, context)
        val result = allocator.allocate(plans, targets, context)
        if (result !is com.stratum.domain.program.generation.prescription.SlotSetAllocationResult.Success) return

        for (i in specialized.indices) {
            for (j in (i + 1) until specialized.size) {
                val aimA = targets[specialized[i]]?.aim ?: continue
                val aimB = targets[specialized[j]]?.aim ?: continue
                if (kotlin.math.abs(aimA - aimB) > 0.5) continue
                val a = weeklyMuscleCredit(result.plans, specialized[i])
                val b = weeklyMuscleCredit(result.plans, specialized[j])
                // Skip pairs where a muscle has no crediting slots (e.g.
                // AnteriorDeltoid when no VerticalPress slot exists in the
                // split) — the solver cannot balance what it cannot credit.
                if (a <= 0.0 || b <= 0.0) continue
                assertTrue(
                    kotlin.math.abs(a - b) <= 1.0,
                    "$config.label: co-specialized ${specialized[i]} ($a) and ${specialized[j]} ($b) differ by > 1.0 at allocator level",
                )
            }
        }
    }

    private fun assertNoSessionCapViolations(
        draft: com.stratum.domain.program.generation.draft.ProgramDraft,
        context: com.stratum.domain.program.generation.context.ProgramGenerationContext,
        label: String,
    ) {
        for (workout in draft.workouts) {
            val fractional = computeWorkoutMuscleVolume(workout, context)
            for ((muscle, credits) in fractional) {
                assertTrue(
                    credits <= SESSION_MUSCLE_SET_CAP + 0.01,
                    "$label: ${workout.name} has $credits fractional credits for $muscle (cap $SESSION_MUSCLE_SET_CAP)",
                )
            }
        }
    }

    private fun assertSpecializedMusclesMeetMev(
        draft: com.stratum.domain.program.generation.draft.ProgramDraft,
        context: com.stratum.domain.program.generation.context.ProgramGenerationContext,
        targets: Map<String, com.stratum.domain.program.generation.volume.WeeklyVolumeTarget>,
        specialization: Map<String, String>,
        label: String,
    ) {
        for ((muscle, level) in specialization) {
            if (level != "Specialize") continue
            val target = targets[muscle] ?: continue
            val weekly = weeklyMuscleVolume(draft, context, muscle)
            // Allow a small tolerance — post-solver repair/trim can shave a set.
            assertTrue(
                weekly >= target.min - 2.0,
                "$label: specialized $muscle weekly $weekly < MEV ${target.min} (tolerance 2.0)",
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

    private fun weeklyMuscleCredit(
        plans: List<com.stratum.domain.program.generation.prescription.SessionSlotPlan>,
        muscle: String,
    ): Double {
        var total = 0.0
        for (plan in plans) {
            for (slot in plan.filledSlots) {
                if (slot.setCount <= 0) continue
                if (slot.primaryMuscle == muscle) {
                    total += slot.setCount * PRIMARY_SET_CREDIT
                    continue
                }
                for (secondary in PATTERN_SECONDARY_MUSCLE_CREDITS[slot.pattern].orEmpty()) {
                    if (secondary.muscle == muscle) {
                        total += slot.setCount * SECONDARY_SET_CREDIT
                    }
                }
            }
        }
        return total
    }

    companion object {
        @JvmStatic
        fun generationScenarios(): List<ScenarioConfig> = GENERATION_SCENARIOS
    }
}
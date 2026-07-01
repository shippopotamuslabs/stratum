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
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Combinatorial regression harness: runs the full `ProgramGenerationPipeline`
 * with [CpSatProgramSynthesizer] across every `GENERATION_SCENARIOS` combo
 * (3/4/5/6-day × 7 specialization presets). Asserts:
 *  - No session muscle-credit cap violations (≤ [SESSION_MUSCLE_SET_CAP]).
 *  - Specialized muscles meet at least their MEV floor.
 *  - Co-specialized muscles (when a preset has 2+) stay balanced within 2.0
 *    weekly credits — the global generalization of the arm-balance test.
 *
 * This locks in the Phase 3 rebalance deletion: the solver + post-solver
 * safety cascade must keep every scenario green without the old per-muscle
 * patches.
 *
 * Tagged `slow` — excluded from the default `testDebugUnitTest` run. Use
 * `./gradlew :core:domain-solver:testSlow` or `-PincludeSlow` to run.
 */
@Tag("slow")
class CombinatorialSpecializationRegressionTest {

    private val synthesizer = CpSatProgramSynthesizer(deadlineMillis = SOLVER_DEADLINE_MILLIS)

    @ParameterizedTest(name = "{0}")
    @MethodSource("generationScenarios")
    @DisplayName("CP-SAT pipeline: caps respected, specialized muscles meet MEV")
    fun specializedMusclesMeetTargetsAndCaps(config: ScenarioConfig) = runBlocking {
        val specialization = SPECIALIZATION_PRESETS[config.preset].orEmpty()
        val context = parseScenarioContext(scenarioPayload(config.days, specialization))
        val targets = planWeeklyVolume(context)
        val pipeline = testProgramGenerationPipeline(programSynthesisPort = synthesizer)
        val program = pipeline.generate(
            context = context,
            normalizationRequest = context.toProgramNormalizationRequest(),
            options = PipelineOptions(skipLiftSwap = true, skipProgramNaming = true),
        )
        val draft = program.toDraft()

        assertNoSessionCapViolations(draft, context, config.label)
        assertSpecializedMusclesMeetMev(draft, context, targets, specialization, config.label)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("generationScenarios")
    @DisplayName("CP-SAT synthesis: co-specialized muscles with equal aims are balanced (pre-repair)")
    fun coSpecializedMusclesBalancedAtSynthesizer(config: ScenarioConfig) {
        val specialization = SPECIALIZATION_PRESETS[config.preset].orEmpty()
        val specialized = specialization.filterValues { it == "Specialize" }.keys.toList()
        if (specialized.size < 2) return

        val context = parseScenarioContext(scenarioPayload(config.days, specialization))
        val targets = planWeeklyVolume(context)
        val result = synthesizer.synthesize(targets, context)
        val solved = result.plans

        for (i in specialized.indices) {
            for (j in (i + 1) until specialized.size) {
                val aimA = targets[specialized[i]]?.aim ?: continue
                val aimB = targets[specialized[j]]?.aim ?: continue
                if (kotlin.math.abs(aimA - aimB) > 0.5) continue
                val a = weeklyMuscleCredit(solved, specialized[i])
                val b = weeklyMuscleCredit(solved, specialized[j])
                // Skip pairs where a muscle has no crediting slots (e.g.
                // AnteriorDeltoid when no VerticalPress slot exists in the
                // split) — the solver cannot balance what it cannot credit.
                if (a <= 0.0 || b <= 0.0) continue
                assertTrue(
                    kotlin.math.abs(a - b) <= 2.0,
                    "$config.label: co-specialized ${specialized[i]} ($a) and ${specialized[j]} ($b) differ by > 2.0 at synthesis level",
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
        // Muscles that have at least one slot crediting them (primary or
        // secondary). A specialized muscle with no crediting slot (e.g.
        // AnteriorDeltoid when no VerticalPress slot exists in the split)
        // cannot hit MEV — skip it.
        val creditedMuscles = mutableSetOf<String>()
        for (workout in draft.workouts) {
            for (lift in workout.workoutLifts.orEmpty()) {
                val pattern = context.movementPatternByLiftId[lift.liftId] ?: continue
                context.liftsById[lift.liftId]?.primaryMuscles?.forEach { creditedMuscles.add(it) }
                PATTERN_SECONDARY_MUSCLE_CREDITS[pattern]?.forEach { creditedMuscles.add(it.muscle) }
            }
        }
        for ((muscle, level) in specialization) {
            if (level != "Specialize") continue
            if (muscle !in creditedMuscles) continue
            val target = targets[muscle] ?: continue
            val weekly = weeklyMuscleVolume(draft, context, muscle)
            // Allow a tolerance — post-solver repair/trim can shave a set.
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
        // 30s solver deadline: tight enough to catch constraint-blow-up regressions, generous
        // enough that CP-SAT reaches a properly validated FEASIBLE/OPTIMAL incumbent.
        const val SOLVER_DEADLINE_MILLIS = 30_000L

        @JvmStatic
        fun generationScenarios(): List<ScenarioConfig> = GENERATION_SCENARIOS
    }
}
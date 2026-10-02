@file:Suppress("FunctionNaming")

package com.stratum.domain.solver

import com.stratum.core.testing.program.SPECIALIZATION_PRESETS
import com.stratum.core.testing.program.ScenarioConfig
import com.stratum.core.testing.program.WEEKDAY_LAYOUT_SCENARIOS
import com.stratum.core.testing.program.activeSolverScenarios
import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.reference.SESSION_MUSCLE_SET_CAP
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleVolume
import com.stratum.domain.program.generation.volume.planWeeklyVolume
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Property checks on the active solver scenario matrix ([activeSolverScenarios]).
 * Default matrix is core (~10); full cartesian with `-PfullSolverScenarios=true`.
 */
class ProgramSynthesisPropertyTest {

    private val pipeline = testProgramGenerationPipeline(
        programSynthesisPort = CpSatProgramSynthesizer(deadlineMillis = SOLVER_DEADLINE_MILLIS),
    )

    @ParameterizedTest(name = "{0}")
    @MethodSource("coreScenarios")
    @DisplayName("synthesis: specialized muscles meet MEV and session caps hold")
    fun specializedMusclesAndCaps(config: ScenarioConfig) = runBlocking {
        val specialization = SPECIALIZATION_PRESETS[config.preset].orEmpty()
        val context =
            parseScenarioContext(
                scenarioPayload(
                    days = config.days,
                    specialization = specialization,
                    weekdayLayout = config.weekdayLayout,
                ),
            )
        val targets = planWeeklyVolume(context)
        val program =
            pipeline.generate(
                context = context,
                normalizationRequest = context.toProgramNormalizationRequest(),
            )
        val draft = program.toDraft()
        assertNoSessionCapViolations(draft, context, config.label)
        for ((muscle, level) in specialization) {
            if (level != "Specialize") continue
            val target = targets[muscle] ?: continue
            val muscleGroup = runCatching { com.stratum.domain.onboarding.MuscleGroup.valueOf(muscle) }.getOrNull() ?: continue
            val volume =
                draft.workouts.sumOf { workout ->
                    computeWorkoutMuscleVolume(workout, context)[muscleGroup] ?: 0.0
                }
            assertTrue(
                volume + 0.01 >= target.min,
                "${config.label}: $muscle volume $volume below MEV ${target.min}",
            )
        }
        assertTrue(
            draft.workouts.all { workout -> !workout.weekday.isNullOrBlank() },
            "${config.label}: every workout should carry a synthesis weekday",
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("weekdayLayoutScenarios")
    @DisplayName("synthesis: alternate weekday layouts produce scheduled workouts")
    fun weekdayLayoutsProduceSchedule(config: ScenarioConfig) = runBlocking {
        val specialization = SPECIALIZATION_PRESETS[config.preset].orEmpty()
        val context =
            parseScenarioContext(
                scenarioPayload(
                    days = config.days,
                    specialization = specialization,
                    weekdayLayout = config.weekdayLayout,
                ),
            )
        val program =
            pipeline.generate(
                context = context,
                normalizationRequest = context.toProgramNormalizationRequest(),
            )
        val draft = program.toDraft()
        assertTrue(
            draft.workouts.all { workout -> !workout.weekday.isNullOrBlank() },
            "${config.label}: every workout should carry a synthesis weekday",
        )
    }

  companion object {
        // Overridden by Gradle systemProperty stratum.solver.deadline (15s smoke / 30s full).
        val SOLVER_DEADLINE_MILLIS =
            System.getProperty("stratum.solver.deadline")?.toLongOrNull() ?: 15_000L

        @JvmStatic
        fun coreScenarios(): List<ScenarioConfig> = activeSolverScenarios()

        @JvmStatic
        fun weekdayLayoutScenarios(): List<ScenarioConfig> = WEEKDAY_LAYOUT_SCENARIOS
    }

    private fun assertNoSessionCapViolations(
        draft: com.stratum.domain.program.generation.draft.ProgramDraft,
        context: com.stratum.domain.program.generation.context.ProgramGenerationContext,
        label: String,
    ) {
        for (workout in draft.workouts) {
            val volume = computeWorkoutMuscleVolume(workout, context)
            for ((muscle, credits) in volume) {
                assertTrue(
                    credits <= SESSION_MUSCLE_SET_CAP + 0.01,
                    "$label ${workout.name}: $muscle credits $credits",
                )
            }
        }
    }
}

@file:Suppress("FunctionNaming")

package com.stratum.domain.solver

import com.stratum.core.testing.program.GENERATION_SCENARIOS
import com.stratum.core.testing.program.SPECIALIZATION_PRESETS
import com.stratum.core.testing.program.ScenarioConfig
import com.stratum.core.testing.program.WEEKDAY_LAYOUT_SCENARIOS
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

class ProgramSynthesisPropertyTest {

    private val pipeline = testProgramGenerationPipeline()

    @ParameterizedTest(name = "{0}")
    @MethodSource("coreScenarios")
    @DisplayName("synthesis: specialized muscles meet MEV and session caps hold")
    fun specializedMusclesAndCaps(config: ScenarioConfig) = runBlocking {
        val specialization = SPECIALIZATION_PRESETS[config.preset].orEmpty()
        val context = parseScenarioContext(scenarioPayload(config.days, specialization))
        val targets = planWeeklyVolume(context)
        val program =
            pipeline.generate(
                context = context,
                normalizationRequest = context.toProgramNormalizationRequest(),
                options = PipelineOptions(skipLiftSwap = true),
            )
        val draft = program.toDraft()
        assertNoSessionCapViolations(draft, context, config.label)
        for ((muscle, level) in specialization) {
            if (level != "Specialize") continue
            val target = targets[muscle] ?: continue
            val volume =
                draft.workouts.sumOf { workout ->
                    computeWorkoutMuscleVolume(workout, context)[muscle] ?: 0.0
                }
            assertTrue(
                volume + 0.01 >= target.min,
                "${config.label}: $muscle volume $volume below MEV ${target.min}",
            )
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("weekdayLayoutScenarios")
    @DisplayName("synthesis: alternate weekday layouts produce scheduled workouts")
    fun weekdayLayoutsProduceSchedule(config: ScenarioConfig) = runBlocking {
        val layoutKey =
            when {
                config.label.contains("mwf") -> "mwf"
                config.label.contains("spread5") -> "spread5"
                else -> "sequential"
            }
        val specialization = SPECIALIZATION_PRESETS[config.preset].orEmpty()
        val context =
            parseScenarioContext(
                scenarioPayload(config.days, specialization, weekdayLayout = layoutKey),
            )
        val program =
            pipeline.generate(
                context = context,
                normalizationRequest = context.toProgramNormalizationRequest(),
                options = PipelineOptions(skipLiftSwap = true),
            )
        val draft = program.toDraft()
        assertTrue(
            draft.workouts.all { workout -> !workout.weekday.isNullOrBlank() },
            "${config.label}: every workout should carry a synthesis weekday",
        )
    }

  companion object {
        @JvmStatic
        fun coreScenarios(): List<ScenarioConfig> = GENERATION_SCENARIOS

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

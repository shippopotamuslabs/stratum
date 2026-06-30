package com.stratum.domain.program.generation

import com.stratum.core.testing.program.SPECIALIZATION_PRESETS
import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.reference.MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE
import com.stratum.domain.program.generation.schema.toDomainProgram
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import com.stratum.domain.program.generation.split.calendarAdjacentTrainingDayPairs
import com.stratum.domain.program.generation.split.trainingWeekdayIndices
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleVolume
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Reproduces the user-reported "same muscle on consecutive calendar training days" complaint
 * measured on the FINAL [com.stratum.domain.program.generation.draft.ProgramDraft] using lift
 * metadata ([computeWorkoutMuscleVolume]) — i.e. exactly what the UI schedule shows — rather
 * than the solver's pattern-credit accounting.
 *
 * Regression guard for the credit-accounting divergence (H1/H5): a selected lift can carry a
 * recovery-relevant secondary muscle (e.g. Hip Thrust → Hamstring) that is absent from
 * `PATTERN_SECONDARY_MUSCLE_CREDITS`, making the exposure invisible to every solver recovery
 * constraint and to the post-solve gate, so the final program stacks that muscle on
 * back-to-back days.
 */
class CalendarAdjacentLiftMetadataRecoveryTest {

    private data class AdjacentViolation(
        val scenario: String,
        val muscle: String,
        val level: String,
        val earlierDay: Weekday,
        val laterDay: Weekday,
        val earlierDose: Double,
        val laterDose: Double,
    )

    @Test
    fun finalDraftHasNoBackToBackSameMuscleExposure() = runTest {
        val scenarios =
            listOf(
                Triple(5, "glutes", "sequential"),
                Triple(5, "legs", "sequential"),
                Triple(5, "legs", "mtwfs"),
                Triple(5, "legs", "sean5"),
                Triple(4, "glutes", "weird4"),
                Triple(3, "legs", "mtwfs"),
            )

        val violations = mutableListOf<AdjacentViolation>()
        for ((days, preset, layout) in scenarios) {
            val context =
                parseScenarioContext(
                    scenarioPayload(days, SPECIALIZATION_PRESETS.getValue(preset), weekdayLayout = layout),
                )
            val pipeline = testProgramGenerationPipeline()
            val normalized =
                pipeline.generate(
                    context = context,
                    normalizationRequest = context.toProgramNormalizationRequest(),
                    options = PipelineOptions(skipLiftSwap = true),
                )
            val draft = normalized.toDraft()
            val program = normalized.toDomainProgram()
            val exposuresByName =
                draft.workouts.associate { workout ->
                    workout.name.orEmpty() to computeWorkoutMuscleVolume(workout, context)
                }
            val workoutByDayIndex =
                program.workouts
                    .filter { it.weekday != null }
                    .associate { Weekday.entries.indexOf(it.weekday!!) to it }
            val adjacentPairs = calendarAdjacentTrainingDayPairs(trainingWeekdayIndices(context.weekdays))
            for ((d1, d2) in adjacentPairs) {
                val w1 = workoutByDayIndex[d1] ?: continue
                val w2 = workoutByDayIndex[d2] ?: continue
                val e1 = exposuresByName[w1.name.orEmpty()].orEmpty()
                val e2 = exposuresByName[w2.name.orEmpty()].orEmpty()
                for (muscle in e1.keys + e2.keys) {
                    val dose1 = e1[muscle] ?: 0.0
                    val dose2 = e2[muscle] ?: 0.0
                    if (dose1 >= MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE &&
                        dose2 >= MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE
                    ) {
                        violations +=
                            AdjacentViolation(
                                scenario = "$days-day $preset $layout",
                                muscle = muscle,
                                level = context.specialization[muscle] ?: "Maintenance",
                                earlierDay = w1.weekday!!,
                                laterDay = w2.weekday!!,
                                earlierDose = dose1,
                                laterDose = dose2,
                            )
                    }
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Final draft schedules the same muscle on calendar-adjacent training days " +
                "(measured with lift metadata, i.e. what the UI shows):\n" +
                violations.joinToString("\n") { v ->
                    "  [${v.scenario}] ${v.muscle} (${v.level}): " +
                        "${v.earlierDay}=${v.earlierDose} -> ${v.laterDay}=${v.laterDose}"
                },
        )
    }
}

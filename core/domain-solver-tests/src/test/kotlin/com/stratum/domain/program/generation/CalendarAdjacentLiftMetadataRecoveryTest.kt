package com.stratum.domain.program.generation

import com.stratum.core.testing.program.SPECIALIZATION_PRESETS
import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.reference.calendarDayIndexGap
import com.stratum.domain.program.generation.reference.muscleRecoveryGapViolated
import com.stratum.domain.program.generation.schema.toDomainProgram
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import com.stratum.domain.program.generation.split.trainingWeekdayIndices
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleSessionStress
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.stratum.domain.solver.CpSatProgramSynthesizer

/**
 * Reproduces the user-reported "same muscle on consecutive calendar training days" complaint
 * measured on the FINAL draft using lift metadata and class-aware recovery (stress class × dose
 * tier), i.e. exactly what the UI schedule shows.
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
    fun finalDraftHasNoBackToBackSameMuscleExposure() = runBlocking {
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
            val pipeline = testProgramGenerationPipeline(programSynthesisPort = CpSatProgramSynthesizer())
            val normalized =
                pipeline.generate(
                    context = context,
                    normalizationRequest = context.toProgramNormalizationRequest(),
                    options = PipelineOptions(skipLiftSwap = true),
                )
            val draft = normalized.toDraft()
            val program = normalized.toDomainProgram()
            val stressByName =
                draft.workouts.associate { workout ->
                    workout.name.orEmpty() to computeWorkoutMuscleSessionStress(workout, context)
                }
            val workoutByDayIndex =
                program.workouts
                    .filter { it.weekday != null }
                    .associate { Weekday.entries.indexOf(it.weekday!!) to it }
            val orderedDays = trainingWeekdayIndices(context.weekdays).sorted()
            for (index in 1 until orderedDays.size) {
                val d1 = orderedDays[index - 1]
                val d2 = orderedDays[index]
                if (d2 - d1 != 1) continue
                val w1 = workoutByDayIndex[d1] ?: continue
                val w2 = workoutByDayIndex[d2] ?: continue
                val stress1 = stressByName[w1.name.orEmpty()].orEmpty()
                val stress2 = stressByName[w2.name.orEmpty()].orEmpty()
                for (muscle in stress1.keys + stress2.keys) {
                    val earlier = stress1[muscle] ?: continue
                    val laterDose = stress2[muscle]?.fractionalSets ?: 0.0
                    if (muscleRecoveryGapViolated(earlier, laterDose, calendarDayIndexGap(d1, d2))) {
                        violations +=
                            AdjacentViolation(
                                scenario = "$days-day $preset $layout",
                                muscle = muscle.name,
                                level = (context.specialization[muscle] ?: SpecializationLevel.Maintenance).name,
                                earlierDay = w1.weekday!!,
                                laterDay = w2.weekday!!,
                                earlierDose = earlier.fractionalSets,
                                laterDose = laterDose,
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

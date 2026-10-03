package com.stratum.domain.program.generation

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.SPECIALIZATION_PRESETS
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.Program
import com.stratum.domain.program.ScheduleDayItem
import com.stratum.domain.program.buildScheduleMuscleExposures
import com.stratum.domain.program.buildWeeklySchedule
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.reference.MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE
import com.stratum.domain.program.generation.reference.MuscleSessionStress
import com.stratum.domain.program.generation.reference.calendarDayIndexGap
import com.stratum.domain.program.generation.reference.minimumRecoveryCalendarGap
import com.stratum.domain.program.generation.reference.muscleRecoveryGapViolated
import com.stratum.domain.program.generation.schema.NormalizedProgram
import com.stratum.domain.program.generation.schema.toDomainProgram
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import com.stratum.domain.program.generation.split.maxPriorityConsecutiveExposureDays
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleSessionStress
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.stratum.domain.solver.CpSatProgramSynthesizer

class ScheduleRecoveryIntegrationTest {

    @Test
    fun fiveDayArmSpecScheduleAvoidsBackToBackTricepsWhenPossible() = runBlocking {
        val context = parseScenarioContext(
            scenarioPayload(5, SPECIALIZATION_PRESETS.getValue("arms")),
        )
        val pipeline = testProgramGenerationPipeline(programSynthesisPort = CpSatProgramSynthesizer())
        val normalized = pipeline.generate(
            context = context,
            normalizationRequest = context.toProgramNormalizationRequest(),
        )
        val draft = normalized.toDraft()
        val exposuresByName =
            draft.workouts.associate { workout ->
                workout.name.orEmpty() to
                    computeWorkoutMuscleSessionStress(workout, context)
                        .mapKeys { (muscle, _) -> muscle.name }
            }
        val scheduled = scheduleFromSynthesisDraft(normalized, draft)
        val violations =
            recoveryViolations(
                scheduled,
                exposuresByName,
                scheduled.trainingWeekdays.sortedBy { day -> Weekday.entries.indexOf(day) },
            )
        val tricepViolations = violations.filter { it.muscle == "Tricep" }
        val mondayLowerTuesdayPush =
            scheduled.workouts.any { it.weekday == Weekday.Monday && it.name == "Lower A" } &&
                scheduled.workouts.any { it.weekday == Weekday.Tuesday && it.name == "Upper Push A" }
        assertTrue(
            tricepViolations.size <= 1,
            "At most one unavoidable next-day tricep exposure on a 5-day arm specialization split: $tricepViolations\n" +
                "Schedule: ${scheduleDebug(scheduled)}\n" +
                "Tricep exposures: ${tricepExposures(exposuresByName)}",
        )
        assertTrue(
            !mondayLowerTuesdayPush,
            "Avoid back-to-back lower touch then push: ${scheduleDebug(scheduled)}",
        )
    }

    @Test
    fun sixDayArmAndCalfSpecLimitsCalfStreakAndRedirectsUpperVolume() = runBlocking {
        val specialization =
            mapOf(
                "Bicep" to "Specialize",
                "Tricep" to "Specialize",
                "Calf" to "Specialize",
            )
        val context = parseScenarioContext(scenarioPayload(6, specialization))
        val pipeline = testProgramGenerationPipeline(programSynthesisPort = CpSatProgramSynthesizer())
        val normalized = pipeline.generate(
            context = context,
            normalizationRequest = context.toProgramNormalizationRequest(),
        )
        val draft = normalized.toDraft()
        val exposuresByName =
            draft.workouts.associate { workout ->
                workout.name.orEmpty() to
                    computeWorkoutMuscleSessionStress(workout, context)
                        .mapKeys { (muscle, _) -> muscle.name }
            }
        val scheduled = scheduleFromSynthesisDraft(normalized, draft)
        val weekdayIndices =
            scheduled.trainingWeekdays.map { day -> Weekday.entries.indexOf(day) }
        val scheduledExposures =
            buildScheduleMuscleExposures(
                workouts = scheduled.workouts,
                weekdayIndices = weekdayIndices,
                workoutMuscleExposures =
                    scheduled.workouts.map { workout ->
                        exposuresByName.getValue(workout.name)
                            .mapValues { (_, stress) -> stress.fractionalSets }
                    },
            )
        val calfStreak = maxPriorityConsecutiveExposureDays(scheduledExposures, setOf("Calf"))
        val calfViolations = recoveryViolations(scheduled, exposuresByName, scheduled.trainingWeekdays.sorted()).filter { it.muscle == "Calf" }
        assertTrue(
            calfViolations.size <= 4,
            "6-day calf spec may have unavoidable class-aware violations on a tight calendar: $calfViolations\n" +
                scheduleDebug(scheduled),
        )
        assertTrue(
            calfStreak <= 6,
            "Calf exposure streak on 6-day training should not exceed training days: $calfStreak\n" +
                scheduleDebug(scheduled),
        )
        val weeklyCredits = exposuresByName.values.fold(mutableMapOf<String, Double>()) { totals, workout ->
            workout.forEach { (muscle, stress) ->
                totals[muscle] = (totals[muscle] ?: 0.0) + stress.fractionalSets
            }
            totals
        }
        assertTrue(
            (weeklyCredits["Bicep"] ?: 0.0) >= 10.0,
            "Weekly credits bicep=${weeklyCredits["Bicep"]} should approach MRV",
        )
        assertTrue(
            (weeklyCredits["Tricep"] ?: 0.0) >= 9.0,
            "Weekly credits tricep=${weeklyCredits["Tricep"]} should approach MRV",
        )
    }

    private fun scheduleFromSynthesisDraft(
        normalized: NormalizedProgram,
        draft: ProgramDraft,
    ): Program = normalized.toDomainProgram()

    private fun tricepExposures(
        exposuresByName: Map<String, Map<String, MuscleSessionStress>>,
    ): Map<String, Double?> =
        exposuresByName.mapValues { (_, doses) -> doses["Tricep"]?.fractionalSets }

    private fun scheduleDebug(program: Program): String =
        buildWeeklySchedule(program)
            .filterIsInstance<ScheduleDayItem.WorkoutDay>()
            .joinToString { "${it.weekday}: ${it.workout.name}" }

    private data class RecoveryViolation(
        val muscle: String,
        val earlierDay: Weekday,
        val laterDay: Weekday,
        val gap: Int,
        val required: Int,
    )

    private fun recoveryViolations(
        program: Program,
        exposuresByName: Map<String, Map<String, MuscleSessionStress>>,
        weekdays: List<Weekday>,
    ): List<RecoveryViolation> {
        val workoutByWeekday =
            program.workouts
                .filter { workout -> workout.weekday != null }
                .associateBy { workout -> workout.weekday!! }
        val violations = mutableListOf<RecoveryViolation>()
        for (muscle in exposuresByName.values.flatMap { it.keys }.toSet()) {
            val sessions = weekdays.mapNotNull { day ->
                val workout = workoutByWeekday[day] ?: return@mapNotNull null
                val stress = exposuresByName[workout.name]?.get(muscle) ?: return@mapNotNull null
                if (stress.fractionalSets < MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE) return@mapNotNull null
                day to stress
            }
            for (i in 1 until sessions.size) {
                val (earlierDay, earlierStress) = sessions[i - 1]
                val (laterDay, laterStress) = sessions[i]
                val gap = calendarDayIndexGap(
                    Weekday.entries.indexOf(earlierDay),
                    Weekday.entries.indexOf(laterDay),
                )
                val required = minimumRecoveryCalendarGap(earlierStress)
                if (muscleRecoveryGapViolated(earlierStress, laterStress.fractionalSets, gap)) {
                    violations.add(RecoveryViolation(muscle, earlierDay, laterDay, gap, required))
                }
            }
        }
        return violations
    }
}

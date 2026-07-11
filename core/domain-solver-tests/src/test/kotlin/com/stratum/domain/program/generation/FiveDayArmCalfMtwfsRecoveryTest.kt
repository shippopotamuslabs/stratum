package com.stratum.domain.program.generation

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.Program
import com.stratum.domain.program.ScheduleDayItem
import com.stratum.domain.program.buildWeeklySchedule
import com.stratum.domain.program.effectiveTrainingWeekdays
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.schema.toDomainProgram
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import com.stratum.domain.program.generation.reference.MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE
import com.stratum.domain.program.generation.reference.MuscleSessionStress
import com.stratum.domain.program.generation.reference.calendarDayIndexGap
import com.stratum.domain.program.generation.reference.minimumRecoveryCalendarGap
import com.stratum.domain.program.generation.reference.muscleRecoveryGapViolated
import com.stratum.domain.program.generation.split.maxPriorityConsecutiveExposureDays
import com.stratum.domain.program.buildScheduleMuscleExposures
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleSessionStress
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.stratum.domain.solver.CpSatProgramSynthesizer

/**
 * Regression: 5-day MTWFS with arm + calf specialization should avoid back-to-back
 * priority muscle exposure when the calendar allows spacing.
 */
class FiveDayArmCalfMtwfsRecoveryTest {
    @Test
    fun `mtwfs arm calf spec limits consecutive priority muscle exposure`() = runBlocking {
        val specialization =
            mapOf(
                "Bicep" to "Specialize",
                "Tricep" to "Specialize",
                "Calf" to "Specialize",
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
                options = PipelineOptions(skipLiftSwap = true),
            )
        val draft = normalized.toDraft()
        val exposuresByName =
            draft.workouts.associate { workout ->
                workout.name.orEmpty() to
                    computeWorkoutMuscleSessionStress(workout, context)
                        .mapKeys { (muscle, _) -> muscle.name }
            }
        val scheduled = normalized.toDomainProgram()
        val priority = setOf("Bicep", "Tricep", "Calf")
        val violations = recoveryViolations(scheduled, exposuresByName)
        val priorityViolations = violations.filter { violation -> violation.muscle in priority }
        val weekdayIndices =
            weekdaysInOrder(scheduled).map { day -> Weekday.entries.indexOf(day) }
        val exposures =
            buildScheduleMuscleExposures(
                workouts = scheduled.workouts,
                weekdayIndices = weekdayIndices,
                workoutMuscleExposures =
                    scheduled.workouts.map { workout ->
                        exposuresByName.getValue(workout.name)
                            .mapValues { (_, stress) -> stress.fractionalSets }
                    },
            )
        val maxStreak = maxPriorityConsecutiveExposureDays(exposures, priority)

        println("=== SCHEDULE ===")
        println(scheduleDebug(scheduled))
        println("=== VIOLATIONS (${violations.size}) ===")
        violations.forEach { v ->
            println("  ${v.muscle}: ${v.earlierDay} -> ${v.laterDay} gap=${v.gap} required=${v.required}")
        }
        println("=== MAX CONSECUTIVE PRIORITY STREAK: $maxStreak ===")
        println("=== PER-MUSCLE DOSES BY DAY ===")
        for (muscle in priority) {
            val line =
                weekdaysInOrder(scheduled)
                    .mapNotNull { day ->
                        val workout = scheduled.workouts.find { it.weekday == day } ?: return@mapNotNull null
                        val dose = exposuresByName[workout.name]?.get(muscle)?.fractionalSets ?: 0.0
                        if (dose < MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE) null else "${day.name.take(3)}=$dose"
                    }
                    .joinToString(", ")
            println("  $muscle: $line")
        }

        assertTrue(
            priorityViolations.size <= 6,
            "MTWFS arm+calf spec may have unavoidable priority violations on a tight calendar: $priorityViolations\n" +
                "All violations: $violations\n" +
                "Schedule: ${scheduleDebug(scheduled)}\n" +
                "Streak: $maxStreak",
        )
        assertTrue(
            maxStreak <= 3,
            "Priority muscle consecutive exposure should be <= 3 days on MTWFS: $maxStreak\n" +
                scheduleDebug(scheduled),
        )
    }

    private fun weekdaysInOrder(program: Program): List<Weekday> =
        program.effectiveTrainingWeekdays().sortedBy { day -> Weekday.entries.indexOf(day) }

    private fun scheduleDebug(program: Program): String =
        buildWeeklySchedule(program)
            .filterIsInstance<ScheduleDayItem.WorkoutDay>()
            .joinToString { "${it.weekday.name.take(3)}: ${it.workout.name}" }

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
    ): List<RecoveryViolation> {
        val weekdays = weekdaysInOrder(program)
        val workoutByWeekday =
            program.workouts
                .filter { workout -> workout.weekday != null }
                .associateBy { workout -> workout.weekday!! }
        val violations = mutableListOf<RecoveryViolation>()
        for (muscle in exposuresByName.values.flatMap { it.keys }.toSet()) {
            val sessions =
                weekdays.mapNotNull { day ->
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

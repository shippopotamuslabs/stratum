package com.stratum.domain.program

import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.buildScheduleMuscleExposures
import com.stratum.domain.program.generation.split.recoveryScheduleSummary
import com.stratum.domain.program.generation.split.upperPullAdjacentLowerCount
import com.stratum.domain.program.optimizeWorkoutWeekdayAssignment
import com.stratum.domain.program.scheduleRecoveryScore
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.generation.reference.minimumRecoveryCalendarGap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProgramScheduleOptimizationTest {

    @Test
    @DisplayName("optimizeWorkoutWeekdayAssignment prefers spreading lower sessions on M/T/W")
    fun spreadsLowerSessionsAcrossWeek() {
        val programId = ProgramId("program-1")
        val lowerA = workout(programId, 0, "Lower A")
        val upper = workout(programId, 1, "Upper A")
        val lowerB = workout(programId, 2, "Lower B")
        val weekdayIndices = listOf(0, 1, 2) // Mon, Tue, Wed
        val optimized = optimizeWorkoutWeekdayAssignment(
            workouts = listOf(lowerA, upper, lowerB),
            weekdayIndices = weekdayIndices,
        )
        assertEquals("Upper A", optimized[1].name)
    }

    @Test
    @DisplayName("optimizeWorkoutWeekdayAssignment spaces triceps across non-consecutive days")
    fun spacesTricepsAcrossWeek() {
        val programId = ProgramId("program-2")
        val lower = workout(programId, 0, "Lower A")
        val push = workout(programId, 1, "Upper Push A")
        val pull = workout(programId, 2, "Upper Pull A")
        val exposures = listOf(
            mapOf("Quad" to 12.0, "Hamstring" to 8.0, "Tricep" to 2.0),
            mapOf("Chest" to 10.0, "Tricep" to 8.0, "AnteriorDeltoid" to 6.0),
            mapOf("Back" to 10.0, "Bicep" to 6.0),
        )
        val weekdayIndices = listOf(0, 1, 2)
        val optimized = optimizeWorkoutWeekdayAssignment(
            workouts = listOf(lower, push, pull),
            weekdayIndices = weekdayIndices,
            workoutMuscleExposures = exposures,
        )
        val exposureByName = mapOf(
            "Lower A" to exposures[0],
            "Upper Push A" to exposures[1],
            "Upper Pull A" to exposures[2],
        )
        val optimizedExposures = optimized.map { exposureByName.getValue(it.name) }
        val scheduledExposures = buildScheduleMuscleExposures(
            workouts = optimized,
            weekdayIndices = weekdayIndices,
            workoutMuscleExposures = optimizedExposures,
        )
        val tricepDays = scheduledExposures
            .filter { it.muscle == "Tricep" }
            .map { it.weekdayIndex }
            .sorted()
        if (tricepDays.size >= 2) {
            val gap = tricepDays[1] - tricepDays[0]
            assertTrue(
                gap >= minimumRecoveryCalendarGap("Tricep", 2.0, 8.0),
                "Tricep sessions on days $tricepDays with gap $gap",
            )
        }
        val backToBackScore = scheduleRecoveryScore(
            workouts = listOf(lower, push, pull),
            weekdayIndices = weekdayIndices,
            workoutMuscleExposures = exposures,
        )
        val optimizedScore = scheduleRecoveryScore(
            workouts = optimized,
            weekdayIndices = weekdayIndices,
            workoutMuscleExposures = optimizedExposures,
        )
        assertTrue(optimizedScore >= backToBackScore)
    }

    @Test
    @DisplayName("five-day arm spec avoids lower touch triceps immediately before upper push")
    fun fiveDayArmSpecAvoidsLowerTouchBeforePush() {
        val programId = ProgramId("program-3")
        val workouts = listOf(
            workout(programId, 0, "Lower A"),
            workout(programId, 1, "Upper Push A"),
            workout(programId, 2, "Upper Pull A"),
            workout(programId, 3, "Lower B"),
            workout(programId, 4, "Upper A"),
        )
        val exposures = listOf(
            mapOf("Tricep" to 2.0, "Quad" to 12.0),
            mapOf("Tricep" to 4.0, "Chest" to 10.0),
            mapOf("Tricep" to 1.0, "Back" to 10.0),
            mapOf("Quad" to 8.0),
            mapOf("Tricep" to 2.0, "Chest" to 6.0),
        )
        val weekdayIndices = listOf(0, 1, 2, 3, 4)
        val optimized = optimizeWorkoutWeekdayAssignment(
            workouts = workouts,
            weekdayIndices = weekdayIndices,
            workoutMuscleExposures = exposures,
            recoveryPriorityMuscles = setOf("Tricep", "Bicep"),
        )
        val scheduleByDay = optimized.mapIndexed { index, workout -> weekdayIndices[index] to workout.name }
        val monday = scheduleByDay.first { it.first == 0 }.second
        val tuesday = scheduleByDay.first { it.first == 1 }.second
        assertTrue(
            !(monday == "Lower A" && tuesday == "Upper Push A"),
            "Schedule: $scheduleByDay",
        )
        val optimizedExposures = optimized.map { workout ->
            exposures[workouts.indexOfFirst { it.name == workout.name }]
        }
        val summary = recoveryScheduleSummary(
            buildScheduleMuscleExposures(optimized, weekdayIndices, optimizedExposures),
            priorityMuscles = setOf("Tricep", "Bicep"),
        )
        assertEquals(0, summary.priorityViolationsByMuscle["Tricep"] ?: 0, "Schedule: $scheduleByDay")
    }

    @Test
    @DisplayName("assignProgramSchedule renumbers session letters by calendar weekday")
    fun assignProgramScheduleRenumbersLettersByWeekday() {
        val programId = ProgramId("program-4")
        val program =
            Program(
                id = programId,
                name = "Test",
                createdAt = java.time.Instant.parse("2026-06-26T12:00:00Z"),
                cycleLength = 6,
                workouts =
                    listOf(
                        workout(programId, 0, "Lower A"),
                        workout(programId, 1, "Upper A"),
                        workout(programId, 2, "Lower B"),
                        workout(programId, 3, "Upper B"),
                        workout(programId, 4, "Lower C"),
                        workout(programId, 5, "Upper C"),
                    ),
            )
        val trainingWeekdays =
            listOf(
                Weekday.Monday,
                Weekday.Tuesday,
                Weekday.Wednesday,
                Weekday.Thursday,
                Weekday.Friday,
                Weekday.Sunday,
            )
        val scheduled = assignProgramSchedule(program, trainingWeekdays)
        val namesInCalendarOrder =
            trainingWeekdays.map { day ->
                scheduled.workouts.first { it.weekday == day }.name
            }
        assertEquals(
            listOf("Lower A", "Upper A", "Lower B", "Upper B", "Lower C", "Upper C"),
            namesInCalendarOrder,
        )
    }

    @Test
    @DisplayName("relabelWorkoutNamesByWeekday sorts letters by weekday not cycle position")
    fun relabelWorkoutNamesByWeekday_sortsByWeekday() {
        val programId = ProgramId("program-5")
        val workouts =
            listOf(
                workout(programId, 0, "Lower C").copy(weekday = Weekday.Monday),
                workout(programId, 1, "Upper B").copy(weekday = Weekday.Tuesday),
                workout(programId, 2, "Lower A").copy(weekday = Weekday.Wednesday),
            )
        val relabeled = relabelWorkoutNamesByWeekday(workouts)
        assertEquals("Lower A", relabeled.first { it.weekday == Weekday.Monday }.name)
        assertEquals("Upper A", relabeled.first { it.weekday == Weekday.Tuesday }.name)
        assertEquals("Lower B", relabeled.first { it.weekday == Weekday.Wednesday }.name)
    }

    @Test
    @DisplayName("five-day split avoids upper pull on calendar day adjacent to lower")
    fun fiveDaySplitAvoidsUpperPullAdjacentLower() {
        val programId = ProgramId("program-5d")
        val workouts =
            listOf(
                workout(programId, 0, "Lower A"),
                workout(programId, 1, "Upper Push A"),
                workout(programId, 2, "Upper Pull A"),
                workout(programId, 3, "Lower B"),
                workout(programId, 4, "Upper A"),
            )
        val exposures =
            listOf(
                mapOf("Quad" to 12.0, "Hamstring" to 8.0, "Bicep" to 2.0),
                mapOf("Chest" to 10.0, "Tricep" to 6.0),
                mapOf("Back" to 10.0, "Bicep" to 8.0),
                mapOf("Quad" to 10.0, "Calf" to 8.0, "Bicep" to 2.0),
                mapOf("Chest" to 8.0, "Back" to 8.0),
            )
        val weekdayIndices = listOf(0, 1, 2, 3, 4)
        val optimized =
            optimizeWorkoutWeekdayAssignment(
                workouts = workouts,
                weekdayIndices = weekdayIndices,
                workoutMuscleExposures = exposures,
                recoveryPriorityMuscles = setOf("Bicep", "Tricep", "Calf"),
            )
        val optimizedExposures =
            optimized.map { workout -> exposures[workouts.indexOfFirst { it.name == workout.name }] }
        val scheduledExposures =
            buildScheduleMuscleExposures(
                workouts = optimized,
                weekdayIndices = weekdayIndices,
                workoutMuscleExposures = optimizedExposures,
            )
        val scheduleByDay =
            optimized.mapIndexed { index, workout -> "${weekdayIndices[index]}=${workout.name}" }
        assertEquals(
            0,
            upperPullAdjacentLowerCount(scheduledExposures),
            "Schedule: $scheduleByDay",
        )
    }

    private fun workout(programId: ProgramId, position: Int, name: String): Workout =
        Workout(
            id = WorkoutId("workout-$position"),
            programId = programId,
            name = name,
            weekday = null,
            position = position,
            workoutLifts = emptyList(),
        )
}

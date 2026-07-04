package com.stratum.domain.program.generation.validation

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.domain.program.SetType
import com.stratum.domain.program.generation.draft.LiftSetDraft
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.draft.ProgramLiftDraft
import com.stratum.domain.program.generation.draft.ProgramWorkoutDraft
import com.stratum.domain.program.generation.reference.MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CalendarAdjacentMuscleExposureValidationTest {

    @Test
    fun flagsMeaningfulSameMuscleExposureOnCalendarAdjacentDays() {
        val context =
            parseScenarioContext(
                scenarioPayload(
                    days = 4,
                    specialization = mapOf("Glute" to "Specialize"),
                    weekdayLayout = "sequential",
                ),
            )
        val program =
            ProgramDraft(
                name = "Test",
                cycleLength = 4,
                workouts =
                    listOf(
                        adjacentWorkout("Lower A", "Monday", "rdl", sets = 5),
                        adjacentWorkout("Lower B", "Tuesday", "rdl", sets = 3),
                        adjacentWorkout("Upper A", "Wednesday", "bench", sets = 4),
                        adjacentWorkout("Upper B", "Thursday", "row", sets = 4),
                    ),
            )

        val issues = validateCalendarAdjacentMuscleExposure(program, context)

        assertEquals(1, issues.size)
        val issue = issues.single()
        assertEquals("CALENDAR_ADJACENT_MUSCLE_EXPOSURE", issue.code)
        assertEquals(ValidationSeverity.Warning, issue.severity)
        assertEquals("Hamstring", issue.details?.get("muscle"))
        assertEquals("Monday", issue.details?.get("earlierDay"))
        assertEquals("Tuesday", issue.details?.get("laterDay"))
        assertTrue(
            (issue.details?.get("earlierDose") as Double) >= MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE,
        )
        assertTrue(
            (issue.details?.get("laterDose") as Double) >= MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE,
        )
    }

    @Test
    fun adjacentExposureDoesNotFailProgramMetricValidation() {
        val context =
            parseScenarioContext(
                scenarioPayload(
                    days = 4,
                    specialization = mapOf("Glute" to "Specialize"),
                    weekdayLayout = "sequential",
                ),
            )
        val program =
            ProgramDraft(
                name = "Test",
                cycleLength = 4,
                workouts =
                    listOf(
                        adjacentWorkout("Lower A", "Monday", "rdl", sets = 5),
                        adjacentWorkout("Lower B", "Tuesday", "rdl", sets = 3),
                        adjacentWorkout("Upper A", "Wednesday", "bench", sets = 4),
                        adjacentWorkout("Upper B", "Thursday", "row", sets = 4),
                    ),
            )

        val report = validateProgramMetrics(program, context)

        assertTrue(report.pass, report.issues.joinToString { it.message })
        assertTrue(
            report.issues.any { it.code == "CALENDAR_ADJACENT_MUSCLE_EXPOSURE" },
        )
    }

    @Test
    fun passesWhenAdjacentDaysStayBelowMeaningfulDose() {
        val context =
            parseScenarioContext(
                scenarioPayload(
                    days = 4,
                    specialization = emptyMap(),
                    weekdayLayout = "sequential",
                ),
            )
        val program =
            ProgramDraft(
                name = "Test",
                cycleLength = 4,
                workouts =
                    listOf(
                        adjacentWorkout("Lower A", "Monday", "squat", sets = 3),
                        adjacentWorkout("Upper A", "Tuesday", "bench", sets = 4),
                        adjacentWorkout("Lower B", "Wednesday", "rdl", sets = 3),
                        adjacentWorkout("Upper B", "Thursday", "row", sets = 4),
                    ),
            )

        val issues = validateCalendarAdjacentMuscleExposure(program, context)

        assertTrue(issues.isEmpty(), issues.joinToString { it.message })
    }

    private fun adjacentWorkout(
        name: String,
        weekday: String,
        liftId: String,
        sets: Int,
    ): ProgramWorkoutDraft =
        ProgramWorkoutDraft(
            name = name,
            weekday = weekday,
            workoutLifts =
                mutableListOf(
                    ProgramLiftDraft(
                        liftId = liftId,
                        position = 0,
                        workoutLiftSets =
                            (1..sets).map { index ->
                                LiftSetDraft(
                                    position = index,
                                    setType = SetType.Standard,
                                    repMin = 8,
                                    repMax = 12,
                                    rirGoal = 2,
                                )
                            }.toMutableList(),
                    ),
                ),
        )
}

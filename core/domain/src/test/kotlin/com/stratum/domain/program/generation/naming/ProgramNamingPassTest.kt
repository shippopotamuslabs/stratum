package com.stratum.domain.program.generation.naming

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.draft.ProgramWorkoutDraft
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProgramNamingPassTest {

    @Test
    fun `applyProgramNames updates program and workout names by position`() {
        val draft =
            ProgramDraft(
                name = "Generated Program",
                cycleLength = 2,
                workouts =
                    listOf(
                        ProgramWorkoutDraft(name = "Session 1", position = 0),
                        ProgramWorkoutDraft(name = "Session 2", position = 1),
                    ),
            )
        val result =
            ProgramNamingResult(
                programName = "4-Day Upper/Lower",
                workoutNames =
                    listOf(
                        ProgramNamingWorkoutName(position = 0, name = "Upper A"),
                        ProgramNamingWorkoutName(position = 1, name = "Lower A"),
                    ),
            )

        val updated = applyProgramNames(draft, result)

        assertEquals("4-Day Upper/Lower", updated.name)
        assertEquals("Upper A", updated.workouts[0].name)
        assertEquals("Lower A", updated.workouts[1].name)
    }

    @Test
    fun `buildProgramNamingRequest includes weekdays and lift metadata`() {
        val context =
            parseScenarioContext(
                scenarioPayload(
                    days = 3,
                    specialization = mapOf("Chest" to "Specialize"),
                    weekdayLayout = "mwf",
                ),
            )
        val draft =
            ProgramDraft(
                name = "Generated Program",
                cycleLength = 1,
                workouts =
                    listOf(
                        ProgramWorkoutDraft(
                            name = "Session 1",
                            position = 0,
                            weekday = "Monday",
                        ),
                    ),
            )

        val request = buildProgramNamingRequest(draft, context)

        assertEquals(context.weekdays, request.namingPayload.intent.trainingWeekdays)
        assertEquals(1, request.namingPayload.program.workouts.size)
        assertEquals("Monday", request.namingPayload.program.workouts.first().weekday)
    }
}

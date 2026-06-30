package com.stratum.domain.program.generation.volume

import com.stratum.domain.program.generation.GenerationContextTestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VolumeBudgetPlannerTest {
    @Test
    fun `calf and tricep specialization on five sixty minute days keeps chest and back at PV aim`() {
        val context =
            GenerationContextTestFixtures.volumePlannerContext(
                profileOverrides =
                    mapOf(
                        "sessionLengthMinutes" to 60,
                        "trainingDaysPerWeek" to 5,
                        "weekdays" to listOf("Monday", "Tuesday", "Wednesday", "Friday", "Saturday"),
                    ),
                specialization = mapOf("Calf" to "Specialize", "Tricep" to "Specialize"),
            )
        val trimmed = planWeeklyVolume(context)

        assertEquals(11.0, trimmed.getValue("Chest").aim)
        assertEquals(11.0, trimmed.getValue("Back").aim)
    }

    @Test
    fun `trims maintenance toward PV before MV while preserving specialized MRV aims`() {
        val context =
            GenerationContextTestFixtures.volumePlannerContext(
                profileOverrides = mapOf("sessionLengthMinutes" to 45),
                specialization = mapOf(
                    "Bicep" to "Specialize",
                    "Tricep" to "Specialize",
                    "Calf" to "Specialize",
                ),
            )
        val muscles = volumePlannerTrainableMuscles(context)
        val unscaled = buildUnscaledWeeklyTargets(muscles, context)
        val trimmed = applyWeeklyTimeBudgetPrecedence(unscaled, context)

        assertEquals(14.0, trimmed.getValue("Bicep").aim)
        assertEquals(14.0, trimmed.getValue("Tricep").aim)
        assertTrue(trimmed.getValue("Chest").aim <= unscaled.getValue("Chest").aim)
        assertTrue(trimmed.getValue("Chest").aim >= maintenancePvGoal(context.volumeLandmarksByMuscle["Chest"], 8.0))
    }
}

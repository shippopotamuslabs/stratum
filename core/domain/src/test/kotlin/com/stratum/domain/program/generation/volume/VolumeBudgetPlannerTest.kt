package com.stratum.domain.program.generation.volume

import com.stratum.core.testing.program.GenerationContextTestFixtures
import com.stratum.core.testing.program.GenerationContextTestFixtures.literatureRc
import com.stratum.core.testing.program.GenerationContextTestFixtures.literaturePv
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VolumeBudgetPlannerTest {
    @Test
    fun `maintenance trim priority trims non anchors before quad chest ham back`() {
        assertTrue(maintenanceTrimPriority("Glute") < maintenanceTrimPriority("Quad"))
        assertTrue(maintenanceTrimPriority("Quad") < maintenanceTrimPriority("Chest"))
        assertTrue(maintenanceTrimPriority("Chest") < maintenanceTrimPriority("Hamstring"))
        assertTrue(maintenanceTrimPriority("Hamstring") < maintenanceTrimPriority("Back"))
        assertTrue(maintenanceTrimPriority("Calf") < maintenanceTrimPriority("Bicep"))
    }

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

        assertEquals(literaturePv("Chest"), trimmed.getValue("Chest").aim)
        assertEquals(literaturePv("Back"), trimmed.getValue("Back").aim)
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

        assertEquals(literatureRc("Bicep"), trimmed.getValue("Bicep").aim)
        assertEquals(literatureRc("Tricep"), trimmed.getValue("Tricep").aim)
        assertTrue(trimmed.getValue("Chest").aim <= unscaled.getValue("Chest").aim)
        assertTrue(
            trimmed.getValue("Back").aim >= trimmed.getValue("Chest").aim,
            "Back should stay at or above chest under trim (solver antagonist preference)",
        )
        assertTrue(
            trimmed.getValue("Hamstring").aim >= trimmed.getValue("Quad").aim,
            "Hamstring should stay at or above quad under trim (solver antagonist preference)",
        )
        assertTrue(
            trimmed.getValue("Quad").aim <= unscaled.getValue("Quad").aim,
            "Quad trims before chest when anchors must shrink",
        )
    }
}

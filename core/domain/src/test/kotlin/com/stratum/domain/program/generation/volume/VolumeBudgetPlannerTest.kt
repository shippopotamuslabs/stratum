package com.stratum.domain.program.generation.volume

import com.stratum.domain.program.generation.GenerationContextTestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VolumeBudgetPlannerTest {
    @Test
    fun `trims maintenance toward MV before touching specialized MRV aims`() {
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
        assertTrue(trimmed.getValue("Chest").aim < unscaled.getValue("Chest").aim)
        assertTrue(trimmed.getValue("Chest").aim >= trimmed.getValue("Chest").min)
    }
}

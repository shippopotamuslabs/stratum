package com.stratum.domain.program.generation.volume

import com.stratum.domain.program.generation.GenerationContextTestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MaintenancePvVolumeBudgetTest {
    @Test
    fun `maintenance muscles keep PV aim when weekly time budget has room`() {
        val context =
            GenerationContextTestFixtures.volumePlannerContext(
                specialization = mapOf("Bicep" to "Specialize"),
            )
        val targets = planWeeklyVolume(context)

        assertEquals(14.0, targets.getValue("Bicep").aim)
        assertEquals(11.0, targets.getValue("Chest").aim)
        assertEquals(11.0, targets.getValue("Back").aim)
    }
}

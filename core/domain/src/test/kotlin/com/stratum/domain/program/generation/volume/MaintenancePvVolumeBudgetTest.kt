package com.stratum.domain.program.generation.volume

import com.stratum.domain.program.generation.GenerationContextTestFixtures
import com.stratum.domain.program.generation.GenerationContextTestFixtures.literatureMrv
import com.stratum.domain.program.generation.GenerationContextTestFixtures.literaturePv
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

        assertEquals(literatureMrv("Bicep"), targets.getValue("Bicep").aim)
        assertEquals(literaturePv("Chest"), targets.getValue("Chest").aim)
        assertEquals(literaturePv("Back"), targets.getValue("Back").aim)
    }
}

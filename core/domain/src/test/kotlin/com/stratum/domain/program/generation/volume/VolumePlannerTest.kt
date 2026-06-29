package com.stratum.domain.program.generation.volume

import com.stratum.domain.program.generation.GenerationContextTestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VolumePlannerTest {
    @Test
    fun `specialized muscles get higher weekly aim than maintenance for the same muscle landmarks`() {
        val specializedCtx = GenerationContextTestFixtures.volumePlannerContext(
            specialization = mapOf("Bicep" to "Specialize"),
        )
        val maintenanceCtx = GenerationContextTestFixtures.volumePlannerContext(
            specialization = mapOf("Bicep" to "Maintenance"),
        )
        val specialized = planWeeklyVolume(specializedCtx).getValue("Bicep")
        val maintenance = planWeeklyVolume(maintenanceCtx).getValue("Bicep")
        assertTrue(specialized.aim > maintenance.aim)
    }

    @Test
    fun `conservative return-to-training profile uses PV for maintenance muscles`() {
        val targets = planWeeklyVolume(
            GenerationContextTestFixtures.volumePlannerContext(
                profileOverrides = mapOf("lastTrained" to "MoreThanAYearAgo"),
                specialization = mapOf(
                    "Bicep" to "Maintenance",
                    "Tricep" to "Maintenance",
                    "Chest" to "Maintenance",
                ),
            ),
        )
        assertEquals(11.0, targets.getValue("Chest").aim)
    }

    @Test
    fun `arm specialization does not reduce leg volume on separate lower days`() {
        val armSpec = planWeeklyVolume(
            GenerationContextTestFixtures.volumePlannerContext(
                specialization = mapOf("Bicep" to "Specialize", "Tricep" to "Specialize"),
            ),
        )
        val neutral = planWeeklyVolume(
            GenerationContextTestFixtures.volumePlannerContext(
                specialization = mapOf("Bicep" to "Maintenance", "Tricep" to "Maintenance"),
            ),
        )
        assertEquals(neutral.getValue("Quad").aim, armSpec.getValue("Quad").aim)
        assertEquals(neutral.getValue("Hamstring").aim, armSpec.getValue("Hamstring").aim)
        assertTrue((armSpec.getValue("Bicep").aim) > (neutral.getValue("Bicep").aim))
    }

    @Test
    fun `arm specialization keeps full weekly back chest targets per-session cap is separate`() {
        val armSpec = planWeeklyVolume(
            GenerationContextTestFixtures.volumePlannerContext(
                specialization = mapOf("Bicep" to "Specialize", "Tricep" to "Specialize"),
            ),
        )
        val neutral = planWeeklyVolume(
            GenerationContextTestFixtures.volumePlannerContext(
                specialization = mapOf("Bicep" to "Maintenance", "Tricep" to "Maintenance"),
            ),
        )
        assertEquals(14.0, armSpec.getValue("Back").aim)
        assertEquals(14.0, armSpec.getValue("Chest").aim)
        assertEquals(neutral.getValue("Back").aim, armSpec.getValue("Back").aim)
        assertEquals(neutral.getValue("Chest").aim, armSpec.getValue("Chest").aim)
        assertTrue((armSpec.getValue("Bicep").aim) > (neutral.getValue("Bicep").aim))
    }
}

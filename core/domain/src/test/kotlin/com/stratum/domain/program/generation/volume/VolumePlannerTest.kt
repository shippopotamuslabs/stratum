package com.stratum.domain.program.generation.volume

import com.stratum.domain.program.generation.GenerationContextTestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VolumePlannerTest {
    @Test
    fun `specialized muscles aim at landmark MRV`() {
        val targets = planWeeklyVolume(
            GenerationContextTestFixtures.volumePlannerContext(
                specialization = mapOf("Bicep" to "Specialize"),
            ),
        )
        assertEquals(14.0, targets.getValue("Bicep").aim)
        assertEquals(14.0, targets.getValue("Bicep").max)
    }

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
    fun `maintenance muscles floor at MV and aim at MAV`() {
        val targets = planWeeklyVolume(
            GenerationContextTestFixtures.volumePlannerContext(
                specialization = mapOf("Chest" to "Maintenance"),
            ),
        )
        assertEquals(4.0, targets.getValue("Chest").min)
        assertEquals(14.0, targets.getValue("Chest").aim)
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
        assertEquals(4.0, targets.getValue("Chest").min)
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
    fun `arm and calf specialization preserves specialized MRV aims while trimming maintenance upper`() {
        val armAndCalfSpec = planWeeklyVolume(
            GenerationContextTestFixtures.volumePlannerContext(
                specialization = mapOf(
                    "Bicep" to "Specialize",
                    "Tricep" to "Specialize",
                    "Calf" to "Specialize",
                ),
            ),
        )
        assertEquals(14.0, armAndCalfSpec.getValue("Bicep").aim)
        assertEquals(14.0, armAndCalfSpec.getValue("Tricep").aim)
        assertTrue(armAndCalfSpec.getValue("Chest").aim <= 14.0)
        assertTrue(armAndCalfSpec.getValue("Chest").aim >= armAndCalfSpec.getValue("Chest").min)
        assertEquals(14.0, armAndCalfSpec.getValue("Quad").aim)
    }

    @Test
    fun `dual arm specialization keeps maintenance chest and back aiming at MAV when budget allows`() {
        val armSpec = planWeeklyVolume(
            GenerationContextTestFixtures.volumePlannerContext(
                specialization = mapOf("Bicep" to "Specialize", "Tricep" to "Specialize"),
            ),
        )
        assertEquals(14.0, armSpec.getValue("Back").aim)
        assertEquals(14.0, armSpec.getValue("Chest").aim)
    }

    @Test
    fun `minimum volume muscles aim at MV below maintenance for the same landmarks`() {
        val minimumCtx = GenerationContextTestFixtures.volumePlannerContext(
            specialization = mapOf("Bicep" to "MinimumVolume"),
        )
        val maintenanceCtx = GenerationContextTestFixtures.volumePlannerContext(
            specialization = mapOf("Bicep" to "Maintenance"),
        )
        val minimum = planWeeklyVolume(minimumCtx).getValue("Bicep")
        val maintenance = planWeeklyVolume(maintenanceCtx).getValue("Bicep")
        assertTrue(minimum.aim < maintenance.aim)
        assertTrue(minimum.max <= maintenance.aim)
    }
}

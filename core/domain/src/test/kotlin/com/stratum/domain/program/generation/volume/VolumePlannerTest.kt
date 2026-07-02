package com.stratum.domain.program.generation.volume

import com.stratum.domain.program.generation.GenerationContextTestFixtures
import com.stratum.domain.program.generation.GenerationContextTestFixtures.literatureMrv
import com.stratum.domain.program.generation.GenerationContextTestFixtures.literaturePv
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
        val expectedMrv = literatureMrv("Bicep")
        assertEquals(expectedMrv, targets.getValue("Bicep").aim)
        assertEquals(expectedMrv, targets.getValue("Bicep").max)
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
    fun `maintenance muscles floor at MV and aim at PV`() {
        val targets = planWeeklyVolume(
            GenerationContextTestFixtures.volumePlannerContext(
                specialization = mapOf("Chest" to "Maintenance"),
            ),
        )
        val chest = targets.getValue("Chest")
        val landmark = GenerationContextTestFixtures.literatureLandmarkContext("Chest")
        assertEquals(landmark.estimatedMv, chest.min)
        assertEquals(literaturePv("Chest"), chest.aim)
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
        assertEquals(literaturePv("Chest"), targets.getValue("Chest").aim)
        assertEquals(
            GenerationContextTestFixtures.literatureLandmarkContext("Chest").estimatedMv,
            targets.getValue("Chest").min,
        )
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
        assertEquals(literatureMrv("Bicep"), armAndCalfSpec.getValue("Bicep").aim)
        assertEquals(literatureMrv("Tricep"), armAndCalfSpec.getValue("Tricep").aim)
        assertTrue(armAndCalfSpec.getValue("Chest").aim <= literaturePv("Chest"))
        assertTrue(armAndCalfSpec.getValue("Chest").aim >= armAndCalfSpec.getValue("Chest").min)
        assertEquals(literaturePv("Quad"), armAndCalfSpec.getValue("Quad").aim)
    }

    @Test
    fun `dual arm specialization keeps maintenance chest and back aiming at PV when budget allows`() {
        val armSpec = planWeeklyVolume(
            GenerationContextTestFixtures.volumePlannerContext(
                specialization = mapOf("Bicep" to "Specialize", "Tricep" to "Specialize"),
            ),
        )
        assertEquals(literaturePv("Back"), armSpec.getValue("Back").aim)
        assertEquals(literaturePv("Chest"), armSpec.getValue("Chest").aim)
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

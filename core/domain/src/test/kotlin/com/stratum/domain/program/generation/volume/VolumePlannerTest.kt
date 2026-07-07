package com.stratum.domain.program.generation.volume

import com.stratum.core.testing.program.GenerationContextTestFixtures
import com.stratum.core.testing.program.GenerationContextTestFixtures.literaturePv
import com.stratum.core.testing.program.GenerationContextTestFixtures.literatureRc
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VolumePlannerTest {
    @Test
    fun `specialized muscles aim at recoverability ceiling RC`() {
        val targets = planWeeklyVolume(
            GenerationContextTestFixtures.volumePlannerContext(
                specialization = mapOf("Bicep" to "Specialize"),
            ),
        )
        val expectedRc = literatureRc("Bicep")
        assertEquals(expectedRc, targets.getValue("Bicep").aim)
        assertEquals(expectedRc, targets.getValue("Bicep").max)
    }

    @Test
    fun `specialized muscles get higher weekly aim than maintenance for the same muscle snapshots`() {
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
        val snapshot = GenerationContextTestFixtures.literatureSnapshotContext("Chest")
        assertEquals(snapshot.retentionFloorMv, chest.min)
        assertEquals(literaturePv("Chest"), chest.aim)
    }

    @Test
    fun `conservative return-to-training profile uses PV for maintenance muscles`() {
        val targets = planWeeklyVolume(
            GenerationContextTestFixtures.volumePlannerContext(
                profileOverrides = mapOf("lastTrained" to "MoreThanThreeMonthsAgo"),
                specialization = mapOf(
                    "Bicep" to "Maintenance",
                    "Tricep" to "Maintenance",
                    "Chest" to "Maintenance",
                ),
            ),
        )
        assertEquals(literaturePv("Chest"), targets.getValue("Chest").aim)
        assertEquals(
            GenerationContextTestFixtures.literatureSnapshotContext("Chest").retentionFloorMv,
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
    fun `arm and calf specialization preserves specialized RC aims while trimming maintenance upper`() {
        val armAndCalfSpec = planWeeklyVolume(
            GenerationContextTestFixtures.volumePlannerContext(
                specialization = mapOf(
                    "Bicep" to "Specialize",
                    "Tricep" to "Specialize",
                    "Calf" to "Specialize",
                ),
            ),
        )
        assertEquals(literatureRc("Bicep"), armAndCalfSpec.getValue("Bicep").aim)
        assertEquals(literatureRc("Tricep"), armAndCalfSpec.getValue("Tricep").aim)
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
    fun `minimum volume muscles aim at MV below maintenance for the same snapshots`() {
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

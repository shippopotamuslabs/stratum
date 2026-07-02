package com.stratum.domain.program.generation.volume

import com.stratum.domain.importing.GlobalVolumePriors
import com.stratum.domain.program.generation.GenerationContextTestFixtures
import com.stratum.domain.program.generation.GenerationContextTestFixtures.literaturePv
import com.stratum.domain.program.generation.GenerationContextTestFixtures.literatureRc
import com.stratum.domain.program.generation.GenerationContextTestFixtures.literatureSnapshotContext
import com.stratum.domain.program.generation.GenerationContextTestFixtures.literatureSpecializeMin
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.VolumeCorrelationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class VolumeTargetPlannerTest {
    @Nested
    inner class ResolveMuscleWeeklyTarget {
        @Test
        fun `Specialize aims at recoverability ceiling RC`() {
            val ctx = GenerationContextTestFixtures.contextWith()
            val target = resolveMuscleWeeklyTarget("Chest", "Specialize", ctx)
            assertEquals(literatureSpecializeMin("Chest"), target.min)
            assertEquals(literatureRc("Chest"), target.aim)
            assertEquals(literatureRc("Chest"), target.max)
        }

        @Test
        fun `Specialize with proven positive volume response still aims at RC`() {
            val ctx = GenerationContextTestFixtures.contextWith(
                volumeCorrelations = listOf(
                    VolumeCorrelationContext("Chest", 0.4, "Proven"),
                ),
            )
            val target = resolveMuscleWeeklyTarget("Chest", "Specialize", ctx)
            assertEquals(literatureRc("Chest"), target.aim)
            assertEquals(literatureRc("Chest"), target.max)
        }

        @Test
        fun `Maintenance targets PV with MV floor`() {
            val ctx = GenerationContextTestFixtures.contextWith(
                specialization = emptyMap(),
                profileOverrides = mapOf(
                    "lastTrained" to "WithinLastMonth",
                    "sessionLengthMinutes" to null,
                ),
            )
            val target = resolveUnscaledTarget("Back", "Maintenance", ctx)
            val snapshot = literatureSnapshotContext("Back")
            assertEquals(snapshot.retentionFloorMv, target.min)
            assertEquals(literaturePv("Back"), target.aim)
            assertEquals(snapshot.maintenanceCeiling, target.max)
        }

        @Test
        fun `Maintenance on a conservative return-to-training profile also targets PV with MV floor`() {
            val ctx = GenerationContextTestFixtures.contextWith(
                specialization = emptyMap(),
                profileOverrides = mapOf(
                    "lastTrained" to "MoreThanAYearAgo",
                    "sessionLengthMinutes" to null,
                ),
            )
            val target = resolveUnscaledTarget("Back", "Maintenance", ctx)
            val snapshot = literatureSnapshotContext("Back")
            assertEquals(snapshot.retentionFloorMv, target.min)
            assertEquals(literaturePv("Back"), target.aim)
            assertEquals(snapshot.maintenanceCeiling, target.max)
        }

        @Test
        fun `falls back to coarse defaults when no snapshot exists for a muscle`() {
            val globals = GlobalVolumePriors.forTrainingAgeName("Intermediate")
            val ctx = GenerationContextTestFixtures.contextWith(
                specialization = emptyMap(),
                profileOverrides = mapOf(
                    "lastTrained" to "WithinLastMonth",
                    "sessionLengthMinutes" to null,
                ),
                literatureVolumeSnapshots = emptyList(),
            )
            val target = resolveUnscaledTarget("Hamstring", "Maintenance", ctx)
            assertEquals(globals.retentionFloorMv, target.min)
            assertEquals(globals.paretoVolumePv, target.aim)
            assertEquals(globals.maintenanceCeiling, target.max)
        }
    }

    @Nested
    inner class ComputeAllWeeklyTargets {
        @Test
        fun `returns a target for every non-Omitted major muscle and any specialized muscle`() {
            val ctx = GenerationContextTestFixtures.contextWith(
                specialization = mapOf("Chest" to "Specialize"),
            )
            val targets = computeAllWeeklyTargets(ctx)
            assertTrue(targets.containsKey("Chest"))
            assertTrue(targets.containsKey("Back"))
            assertTrue(targets.containsKey("Quad"))
            assertTrue(targets.containsKey("Hamstring"))
            assertTrue(targets.containsKey("Glute"))
            assertTrue(targets.containsKey("Tricep"))
            assertTrue(targets.containsKey("Bicep"))
            assertTrue(targets.containsKey("LateralDeltoid"))
            assertTrue(targets.containsKey("PosteriorDeltoid"))
            assertTrue(targets.containsKey("AnteriorDeltoid"))
        }

        @Test
        fun `excludes Omitted muscles from the target map`() {
            val ctx = GenerationContextTestFixtures.contextWith(
                specialization = mapOf("Calf" to "Omit"),
            )
            val targets = computeAllWeeklyTargets(ctx)
            assertFalse(targets.containsKey("Calf"))
        }

        @Test
        fun `specialized muscles get a higher aim than maintenance muscles with the same snapshot`() {
            val ctx = GenerationContextTestFixtures.contextWith(
                specialization = mapOf("Chest" to "Specialize", "Back" to "Maintenance"),
            )
            val targets = computeAllWeeklyTargets(ctx)
            val chest = targets.getValue("Chest")
            val back = targets.getValue("Back")
            assertTrue(chest.aim > back.aim, "Specialized Chest aim should exceed Maintenance Back aim")
        }

        @Test
        fun `includes a muscle that appears only via a literature snapshot (not a major muscle)`() {
            val ctx = GenerationContextTestFixtures.contextWith(
                specialization = emptyMap(),
                profileOverrides = mapOf("sessionLengthMinutes" to null),
                literatureVolumeSnapshots = listOf(GenerationContextTestFixtures.literatureSnapshotContext("Calf")),
            )
            val targets = computeAllWeeklyTargets(ctx)
            assertTrue(targets.containsKey("Calf"))
            assertEquals(literaturePv("Calf"), targets.getValue("Calf").aim)
        }
    }

    @Nested
    inner class AvailablePatternsByMuscle {
        @Test
        fun `maps each muscle to the movement patterns of enabled lifts that hit it`() {
            val ctx = GenerationContextTestFixtures.contextWith()
            val patterns = availablePatternsByMuscle(ctx)
            assertTrue("HorizontalPress" in patterns.getValue("Chest"))
            assertTrue("HorizontalPress" in patterns.getValue("Tricep"))
            assertTrue("HorizontalPull" in patterns.getValue("Back"))
            assertTrue("HorizontalPull" in patterns.getValue("Bicep"))
        }

        @Test
        fun `returns an empty map when the user has no enabled lifts`() {
            val ctx = GenerationContextTestFixtures.contextWith(
                provenLifts = emptyList(),
                availableLifts = emptyList(),
            )
            val patterns = availablePatternsByMuscle(ctx)
            assertTrue(patterns.isEmpty())
        }

        @Test
        fun `deduplicates patterns across primary and secondary contributions`() {
            val ctx = GenerationContextTestFixtures.contextWith(
                provenLifts = listOf(
                    LiftContext(
                        id = "bench",
                        name = "bench",
                        movementPattern = "HorizontalPress",
                        movementPatterns = listOf("HorizontalPress"),
                        primaryMuscles = listOf("Chest"),
                        secondaryMuscles = listOf("Tricep"),
                    ),
                    LiftContext(
                        id = "inc-press",
                        name = "inc-press",
                        movementPattern = "InclinePress",
                        movementPatterns = listOf("InclinePress"),
                        primaryMuscles = listOf("Chest"),
                        secondaryMuscles = listOf("AnteriorDeltoid"),
                    ),
                ),
            )
            val patterns = availablePatternsByMuscle(ctx)
            val chest = patterns.getValue("Chest")
            assertEquals(1, chest.count { it == "HorizontalPress" })
            assertTrue("InclinePress" in chest)
        }
    }
}

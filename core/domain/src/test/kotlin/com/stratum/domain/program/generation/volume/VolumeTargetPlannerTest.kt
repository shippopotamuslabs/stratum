package com.stratum.domain.program.generation.volume

import com.stratum.domain.program.generation.GenerationContextTestFixtures
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.VolumeLandmarkContext
import com.stratum.domain.program.generation.context.VolumeLandmarkSource
import com.stratum.domain.program.generation.context.VolumeResponseContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class VolumeTargetPlannerTest {
    @Nested
    inner class ResolveMuscleWeeklyTarget {
        @Test
        fun `Specialize aims at landmark MRV`() {
            val ctx = GenerationContextTestFixtures.contextWith()
            val target = resolveMuscleWeeklyTarget("Chest", "Specialize", ctx)
            assertEquals(12.0, target.min)
            assertEquals(20.0, target.aim)
            assertEquals(20.0, target.max)
        }

        @Test
        fun `Specialize with proven positive volume response still aims at MRV`() {
            val ctx = GenerationContextTestFixtures.contextWith(
                volumeResponses = listOf(
                    VolumeResponseContext("Chest", 0.4, "Proven"),
                ),
            )
            val target = resolveMuscleWeeklyTarget("Chest", "Specialize", ctx)
            assertEquals(20.0, target.aim)
            assertEquals(20.0, target.max)
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
            assertEquals(4.0, target.min)
            assertEquals(11.0, target.aim)
            assertEquals(14.0, target.max)
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
            assertEquals(4.0, target.min)
            assertEquals(11.0, target.aim)
            assertEquals(14.0, target.max)
        }

        @Test
        fun `falls back to coarse defaults when no landmark exists for a muscle`() {
            val ctx = GenerationContextTestFixtures.contextWith(
                specialization = emptyMap(),
                profileOverrides = mapOf(
                    "lastTrained" to "WithinLastMonth",
                    "sessionLengthMinutes" to null,
                ),
                literatureVolumeLandmarks = emptyList(),
            )
            val target = resolveUnscaledTarget("Hamstring", "Maintenance", ctx)
            assertEquals(3.6, target.min)
            assertEquals(11.0, target.aim)
            assertEquals(14.0, target.max)
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
        fun `specialized muscles get a higher aim than maintenance muscles with the same landmark`() {
            val ctx = GenerationContextTestFixtures.contextWith(
                specialization = mapOf("Chest" to "Specialize", "Back" to "Maintenance"),
            )
            val targets = computeAllWeeklyTargets(ctx)
            val chest = targets.getValue("Chest")
            val back = targets.getValue("Back")
            assertTrue(chest.aim > back.aim, "Specialized Chest aim should exceed Maintenance Back aim")
        }

        @Test
        fun `includes a muscle that appears only via a literature landmark (not a major muscle)`() {
            val ctx = GenerationContextTestFixtures.contextWith(
                specialization = emptyMap(),
                profileOverrides = mapOf("sessionLengthMinutes" to null),
                literatureVolumeLandmarks = listOf(
                    VolumeLandmarkContext("Calf", 6.0, 10.0, 16.0, 8.0, VolumeLandmarkSource.Literature),
                ),
            )
            val targets = computeAllWeeklyTargets(ctx)
            assertTrue(targets.containsKey("Calf"))
            assertEquals(8.0, targets.getValue("Calf").aim)
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

package com.stratum.core.infrastructure.seed

import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * FPR-S2-008 — seed asset bijective fatigue rewrite.
 * FPR-S2-007 — seeder missing fatigueProfile → Low via [LiftCatalogSeeder.mapSeedDto].
 */
class FatigueProfileSeedAssetTest {

    private val seedFile = File("src/main/assets/lifts/seed-lifts.json")

    @Test
    @DisplayName("FPR-S2-008 seed-lifts.json bijective fatigue counts")
    fun fprS2008_seedDistribution() {
        assertTrue(seedFile.exists(), "seed-lifts.json missing at ${seedFile.absolutePath}")
        val root = Json.parseToJsonElement(seedFile.readText()).jsonArray
        assertEquals(266, root.size)

        var high = 0
        var medium = 0
        var low = 0
        for (element in root) {
            val obj = element.jsonObject
            assertFalse(
                "kinematicProfile" in obj,
                "row must not contain field kinematicProfile",
            )
            assertTrue(
                "fatigueProfile" in obj,
                "every row must contain field fatigueProfile",
            )
            when (obj.getValue("fatigueProfile").jsonPrimitive.content) {
                "High" -> high++
                "Medium" -> medium++
                "Low" -> low++
                else ->
                    error(
                        "unexpected fatigueProfile value: " +
                            obj.getValue("fatigueProfile").jsonPrimitive.content,
                    )
            }
        }
        // Bijective rewrite snapshot (catalog content lock for this ticket).
        assertEquals(10, high, "High fatigue seed count")
        assertEquals(131, medium, "Medium fatigue seed count")
        assertEquals(125, low, "Low fatigue seed count")
        assertEquals(high + medium + low, root.size, "all rows must use High/Medium/Low")
    }

    @Test
    @DisplayName("FPR-S2-007 seeder maps missing fatigueProfile to Low")
    fun fprS2007_missingFatigueDefaultsLow() {
        val dto =
            LiftSeedDto(
                id = "custom-missing-fatigue",
                name = "Missing Fatigue Lift",
                movementPatterns = listOf(MovementPattern.ElbowFlexion),
                primaryMuscles = listOf(MuscleGroup.Bicep),
                // fatigueProfile intentionally omitted (null = missing asset field)
            )
        assertEquals(null, dto.fatigueProfile, "DTO must omit profile so seeder applies default")

        val mapped = LiftCatalogSeeder.mapSeedDto(dto)
        assertNotNull(mapped)
        assertEquals(
            FatigueProfile.Low,
            mapped!!.fatigueProfile,
            "LiftCatalogSeeder.mapSeedDto must map missing fatigueProfile to Low",
        )
    }
}

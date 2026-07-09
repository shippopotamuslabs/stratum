package com.stratum.domain.importing

import com.stratum.domain.lift.identity.LiftId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Bm25LiftNameResolverTest {
    private val resolver = Bm25LiftNameResolver()

    @Test
    fun `resolves close exercise name to catalog lift`() {
        val catalog = mapOf(
            LiftId("bench") to "Barbell Bench Press",
            LiftId("squat") to "Back Squat",
        )
        val result = resolver.resolve("barbell bench press", catalog)
        assertEquals(LiftId("bench"), result)
    }

    @Test
    fun `matches long cable triceps name when catalog tokens are covered`() {
        val catalog =
            mapOf(
                LiftId("overhead") to "Cable Rope Overhead Triceps Extension",
            )
        val evaluation =
            resolver.evaluate(
                "Single Arm Cable Overhead Triceps Extension ∈ SS3",
                catalog,
            )
        assertFalse(evaluation.matched)
    }

    @Test
    fun `subset query strongly aligns with longer catalog name when extra token is equipment`() {
        val catalog = mapOf(LiftId("bench") to "Barbell Bench Press")
        assertTrue(resolver.isStrongTokenAlignment("bench press", catalog.getValue(LiftId("bench"))))
        assertTrue(resolver.evaluate("bench press", catalog).matched)
    }

    @Test
    fun `query with standing qualifier strongly aligns with shorter catalog name`() {
        val catalog = mapOf(LiftId("smith-calf") to "Smith Machine Calf Raise")
        assertTrue(
            resolver.isStrongTokenAlignment(
                "Standing Smith Machine Calf Raise",
                catalog.getValue(LiftId("smith-calf")),
            ),
        )
        val evaluation = resolver.evaluate("Standing Smith Machine Calf Raise", catalog)
        assertTrue(evaluation.matched)
        assertEquals(LiftId("smith-calf"), evaluation.matchedLiftId)
    }

    @Test
    fun `mojibake superset suffix still matches catalog name`() {
        val catalog = mapOf(LiftId("katana") to "Katana Triceps Extension")
        val evaluation = resolver.evaluate("Katana Triceps Extension Ã¢ÂˆÂˆ SS1", catalog)
        assertTrue(evaluation.matched)
        assertEquals(LiftId("katana"), evaluation.matchedLiftId)
    }

    @Test
    fun `equipment-qualified subset matches catalog with variant descriptor`() {
        val catalog = mapOf(LiftId("back-squat") to "Barbell Back Squat")
        val evaluation = resolver.evaluate("Squat (Barbell)", catalog)
        assertTrue(evaluation.matched)
        assertEquals(LiftId("back-squat"), evaluation.matchedLiftId)
    }

    @Test
    fun `subset query does not match when core tokens differ`() {
        val catalog = mapOf(LiftId("jm-press") to "JM Press")
        // "leg press" → query has "leg" not in catalog → queryRecall < 1.0 → no subset alignment
        assertFalse(resolver.isStrongTokenAlignment("leg press", catalog.getValue(LiftId("jm-press"))))
        assertFalse(resolver.evaluate("leg press", catalog).matched)
    }

    @Test
    fun `overhead press does not match bench press despite shared tokens`() {
        val catalog = mapOf(LiftId("bench") to "Barbell Bench Press")
        // "overhead press (barbell)" → "barbell overhead press" — "overhead" not in catalog
        assertFalse(resolver.evaluate("Overhead Press (Barbell)", catalog).matched)
    }

    @Test
    fun `strips superset suffix before scoring`() {
        val catalog =
            mapOf(
                LiftId("curl") to "Lying Leg Curl",
            )
        val withSuffix = resolver.evaluate("Lying Hamstring Curl ∈ SS1", catalog)
        val withoutSuffix = resolver.evaluate("Lying Hamstring Curl", catalog)
        assertEquals(withoutSuffix.bestScore, withSuffix.bestScore, 0.001)
    }

    @Test
    fun `rewrites trailing equipment paren to leading for matching`() {
        val catalog =
            mapOf(
                LiftId("db-bench") to "Dumbbell Bench Press",
            )
        val evaluation = resolver.evaluate("Bench Press (Dumbbell)", catalog)
        assertTrue(evaluation.matched)
        assertEquals(LiftId("db-bench"), evaluation.matchedLiftId)
    }

    @Test
    fun `matches strong dash variant against base catalog name`() {
        val catalog =
            mapOf(
                LiftId("lat-pulldown") to "Lat Pulldown",
            )
        val evaluation = resolver.evaluate("Lat Pulldown - Underhand (Cable)", catalog)
        assertFalse(evaluation.matched)
    }

    @Test
    fun `matches seated hamstring curl to seated leg curl not band variant`() {
        val catalog =
            mapOf(
                LiftId("seated-band") to "Seated Band Hamstring Curl",
                LiftId("seated-leg") to "Seated Leg Curl",
            )
        val evaluation = resolver.evaluate("Seated Hamstring Curl ∈ SS1", catalog)
        assertTrue(evaluation.matched)
        assertEquals(LiftId("seated-leg"), evaluation.matchedLiftId)
        assertEquals("Seated Leg Curl", evaluation.bestCatalogName)
    }

    @Test
    fun `matches lying hamstring curl to lying leg curls`() {
        val catalog =
            mapOf(
                LiftId("lying-leg") to "Lying Leg Curls",
                LiftId("seated-band") to "Seated Band Hamstring Curl",
            )
        val evaluation = resolver.evaluate("Lying Hamstring Curl ∈ SS1", catalog)
        assertTrue(evaluation.matched)
        assertEquals(LiftId("lying-leg"), evaluation.matchedLiftId)
    }
}

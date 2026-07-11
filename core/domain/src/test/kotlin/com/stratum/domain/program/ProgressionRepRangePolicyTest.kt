package com.stratum.domain.program

import com.stratum.domain.program.ProgressionRepRangePolicy.WorkingSetRepSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProgressionRepRangePolicyTest {

    @Test
    @DisplayName("storesRepRange: Linear always, Double only when top")
    fun storesRepRangePolicy() {
        assertTrue(ProgressionRepRangePolicy.storesRepRange(ProgressionScheme.Linear, isTopSet = false))
        assertTrue(ProgressionRepRangePolicy.storesRepRange(ProgressionScheme.Linear, isTopSet = true))
        assertTrue(ProgressionRepRangePolicy.storesRepRange(ProgressionScheme.Double, isTopSet = true))
        assertFalse(ProgressionRepRangePolicy.storesRepRange(ProgressionScheme.Double, isTopSet = false))
    }

    @Test
    @DisplayName("normalizeRange enforces max >= min and max-only becomes usable band")
    fun normalizeRangeEdges() {
        assertEquals(0 to 0, ProgressionRepRangePolicy.normalizeRange(ProgressionScheme.Double, false, 8, 12))
        assertEquals(8 to 12, ProgressionRepRangePolicy.normalizeRange(ProgressionScheme.Double, true, 8, 12))
        assertEquals(8 to 8, ProgressionRepRangePolicy.normalizeRange(ProgressionScheme.Linear, true, 8, 5))
        assertEquals(12 to 12, ProgressionRepRangePolicy.normalizeRange(ProgressionScheme.Linear, true, 0, 12))
    }

    @Test
    @DisplayName("normalizeRange floors positive bands at MIN_STORED_REP_MIN (5)")
    fun normalizeRangeEnforcesStoredFloor() {
        assertEquals(
            ProgressionRepRangePolicy.MIN_STORED_REP_MIN to 8,
            ProgressionRepRangePolicy.normalizeRange(ProgressionScheme.Linear, true, 3, 8),
        )
        assertEquals(
            ProgressionRepRangePolicy.MIN_STORED_REP_MIN to ProgressionRepRangePolicy.MIN_STORED_REP_MIN,
            ProgressionRepRangePolicy.normalizeRange(ProgressionScheme.Double, true, 4, 4),
        )
        // Max-only below floor becomes floor/floor.
        assertEquals(
            ProgressionRepRangePolicy.MIN_STORED_REP_MIN to ProgressionRepRangePolicy.MIN_STORED_REP_MIN,
            ProgressionRepRangePolicy.normalizeRange(ProgressionScheme.Linear, true, 0, 4),
        )
        // Backoff / zero band stays 0/0.
        assertEquals(0 to 0, ProgressionRepRangePolicy.normalizeRange(ProgressionScheme.Double, false, 4, 4))
    }

    @Test
    @DisplayName("normalize Double: first set keeps band as top; rest 0/0")
    fun normalizeDouble() {
        val input =
            listOf(
                WorkingSetRepSpec(8, 12, isTopSet = true),
                WorkingSetRepSpec(8, 12, isTopSet = false),
                WorkingSetRepSpec(10, 10, isTopSet = false),
            )
        val out = ProgressionRepRangePolicy.normalize(ProgressionScheme.Double, input)

        assertEquals(3, out.size)
        assertTrue(out[0].isTopSet)
        assertEquals(8, out[0].repMin)
        assertEquals(12, out[0].repMax)
        out.drop(1).forEach { set ->
            assertFalse(set.isTopSet)
            assertEquals(0, set.repMin)
            assertEquals(0, set.repMax)
        }
    }

    @Test
    @DisplayName("normalize Linear: shared band on every set; first is progression anchor")
    fun normalizeLinear() {
        val input =
            listOf(
                WorkingSetRepSpec(5, 8, isTopSet = true),
                WorkingSetRepSpec(0, 0, isTopSet = false),
            )
        val out = ProgressionRepRangePolicy.normalize(ProgressionScheme.Linear, input)

        assertTrue(out[0].isTopSet)
        assertFalse(out[1].isTopSet)
        assertEquals(5, out[0].repMin)
        assertEquals(8, out[0].repMax)
        assertEquals(5, out[1].repMin)
        assertEquals(8, out[1].repMax)
    }

    @Test
    @DisplayName("normalize does not invent defaults when all ranges are zero")
    fun normalizeDoesNotInventDefaults() {
        val input =
            listOf(
                WorkingSetRepSpec(0, 0, isTopSet = false),
                WorkingSetRepSpec(0, 0, isTopSet = false),
            )
        val out = ProgressionRepRangePolicy.normalize(ProgressionScheme.Double, input)
        assertTrue(out[0].isTopSet)
        assertEquals(0, out[0].repMin)
        assertEquals(0, out[0].repMax)
    }

    @Test
    @DisplayName("normalize uses explicit fallback when remaining sets have no band")
    fun normalizeUsesFallbackBand() {
        val input = listOf(WorkingSetRepSpec(0, 0, isTopSet = false))
        val out =
            ProgressionRepRangePolicy.normalize(
                scheme = ProgressionScheme.Double,
                orderedSets = input,
                fallbackRepMin = 5,
                fallbackRepMax = 5,
            )
        assertEquals(5, out[0].repMin)
        assertEquals(5, out[0].repMax)
    }

    @Test
    @DisplayName("applyBand uses user-authored values not prior top preference")
    fun applyBandFromUser() {
        val out =
            ProgressionRepRangePolicy.applyBand(
                scheme = ProgressionScheme.Linear,
                setCount = 2,
                repMin = 6,
                repMax = 4, // inverted → clamped
            )
        assertEquals(6, out[0].repMin)
        assertEquals(6, out[0].repMax)
        assertEquals(6, out[1].repMin)
        assertEquals(6, out[1].repMax)
    }

    @Test
    @DisplayName("applyBand floors sub-MIN_STORED_REP_MIN user input")
    fun applyBandFloorsLowRepMin() {
        val out =
            ProgressionRepRangePolicy.applyBand(
                scheme = ProgressionScheme.Double,
                setCount = 2,
                repMin = 4,
                repMax = 6,
            )
        assertEquals(ProgressionRepRangePolicy.MIN_STORED_REP_MIN, out[0].repMin)
        assertEquals(6, out[0].repMax)
        assertEquals(0, out[1].repMin)
        assertEquals(0, out[1].repMax)
    }

    @Test
    @DisplayName("generate Double zeros non-top; Linear keeps planned band on all")
    fun generateBothSchemes() {
        val doubleSets =
            ProgressionRepRangePolicy.generate(
                scheme = ProgressionScheme.Double,
                setCount = 3,
                plannedRepMin = 8,
                plannedRepMax = 14,
            )
        assertTrue(doubleSets[0].isTopSet)
        assertEquals(8, doubleSets[0].repMin)
        assertEquals(14, doubleSets[0].repMax)
        doubleSets.drop(1).forEach {
            assertFalse(it.isTopSet)
            assertEquals(0, it.repMin)
            assertEquals(0, it.repMax)
        }

        val linearSets =
            ProgressionRepRangePolicy.generate(
                scheme = ProgressionScheme.Linear,
                setCount = 3,
                plannedRepMin = 5,
                plannedRepMax = 8,
            )
        assertTrue(linearSets.all { it.repMin == 5 && it.repMax == 8 })
        assertTrue(linearSets[0].isTopSet)
        assertTrue(linearSets.drop(1).none { it.isTopSet })

        val floored =
            ProgressionRepRangePolicy.generate(
                scheme = ProgressionScheme.Double,
                setCount = 2,
                plannedRepMin = 4,
                plannedRepMax = 6,
            )
        assertEquals(ProgressionRepRangePolicy.MIN_STORED_REP_MIN, floored[0].repMin)
        assertEquals(6, floored[0].repMax)
        assertEquals(0, floored[1].repMin)
    }

    @Test
    @DisplayName("resolveSharedBand returns 0/0 without fallback when empty")
    fun resolveBandNoInvent() {
        val (min, max) =
            ProgressionRepRangePolicy.resolveSharedBand(
                listOf(WorkingSetRepSpec(0, 0, isTopSet = false)),
            )
        assertEquals(0, min)
        assertEquals(0, max)
    }
}

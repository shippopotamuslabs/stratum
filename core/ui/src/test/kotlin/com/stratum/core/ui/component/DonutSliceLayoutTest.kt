package com.stratum.core.ui.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class DonutSliceLayoutTest {

    @Test
    @DisplayName("sweeps start at 12 o'clock and consume the full circle")
    fun sweepsCoverCircleFromTop() {
        val layouts = layoutDonutSlices(listOf(0.45f, 0.22f, 0.23f, 0.10f))

        assertEquals(4, layouts.size)
        assertEquals(-90f, layouts.first().startAngleDeg, 0.01f)
        assertEquals(162f, layouts[0].sweepAngleDeg, 0.01f)
        assertEquals(79.2f, layouts[1].sweepAngleDeg, 0.01f)
        val end = layouts.last().startAngleDeg + layouts.last().sweepAngleDeg
        assertEquals(270f, end, 0.01f)
    }

    @Test
    @DisplayName("tiny slices keep geometry but do not offer an on-ring label")
    fun tinySlicesSkipLabels() {
        val layouts = layoutDonutSlices(listOf(0.90f, 0.04f, 0.06f))

        assertTrue(layouts[0].showLabel)
        assertFalse(layouts[1].showLabel)
        assertFalse(layouts[2].showLabel)
        assertEquals(-90f + 324f, layouts[1].startAngleDeg, 0.01f)
    }

    @Test
    @DisplayName("label angle sits at the midpoint of each sweep")
    fun labelAngleIsMidSweep() {
        val layouts = layoutDonutSlices(listOf(0.5f, 0.5f))

        assertEquals(-90f + 90f, layouts[0].labelAngleDeg, 0.01f)
        assertEquals(90f + 90f, layouts[1].labelAngleDeg, 0.01f)
    }
}

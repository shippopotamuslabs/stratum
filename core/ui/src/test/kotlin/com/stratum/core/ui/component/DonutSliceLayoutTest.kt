package com.stratum.core.ui.component

import org.junit.jupiter.api.Assertions.assertEquals
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
    @DisplayName("zero and tiny slices keep geometry so later sweeps stay aligned")
    fun tinySlicesKeepGeometry() {
        val layouts = layoutDonutSlices(listOf(0.90f, 0.04f, 0.06f))

        assertEquals(324f, layouts[0].sweepAngleDeg, 0.01f)
        assertEquals(14.4f, layouts[1].sweepAngleDeg, 0.01f)
        assertEquals(-90f + 324f, layouts[1].startAngleDeg, 0.01f)
    }
}

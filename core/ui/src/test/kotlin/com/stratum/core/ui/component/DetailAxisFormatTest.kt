package com.stratum.core.ui.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class DetailAxisFormatTest {
    @Test
    @DisplayName("progress axis collapses near-zero and avoids -0")
    fun progressNearZero() {
        assertEquals("0", formatDetailAxisY(0.0, DetailYScale.Progress))
        assertEquals("0", formatDetailAxisY(-1e-15, DetailYScale.Progress))
    }

    @Test
    @DisplayName("progress axis uses enough precision for sub-1 values")
    fun progressSmall() {
        assertEquals("0.12", formatDetailAxisY(0.12, DetailYScale.Progress))
        assertEquals("0.015", formatDetailAxisY(0.015, DetailYScale.Progress))
        assertEquals("0.0012", formatDetailAxisY(0.0012, DetailYScale.Progress))
    }

    @Test
    @DisplayName("weight and volume format sensibly")
    fun weightAndVolume() {
        assertEquals("225", formatDetailAxisY(225.0, DetailYScale.Weight))
        assertEquals("12", formatDetailAxisY(12.0, DetailYScale.Volume))
        assertEquals("7.5", formatDetailAxisY(7.5, DetailYScale.Volume))
    }

    @Test
    @DisplayName("progressYRange always includes 0 for all-positive data")
    fun progressRangeIncludesZeroWhenPositive() {
        val (minY, maxY) = progressYRange(dataMin = 0.05, dataMax = 0.20)
        assertTrue(minY < 0.0, "floor below 0 so baseline is visible: minY=$minY")
        assertTrue(maxY > 0.20, "ceiling above data: maxY=$maxY")
        assertTrue(minY <= 0.0 && maxY >= 0.0)
    }

    @Test
    @DisplayName("progressYRange always includes 0 for mixed sign data")
    fun progressRangeIncludesZeroWhenMixed() {
        val (minY, maxY) = progressYRange(dataMin = -0.09, dataMax = 0.37)
        assertTrue(minY < -0.09)
        assertTrue(maxY > 0.37)
        assertTrue(minY <= 0.0 && maxY >= 0.0)
    }

    @Test
    @DisplayName("progressYRange uses min span for near-flat series")
    fun progressRangeMinSpan() {
        val (minY, maxY) = progressYRange(dataMin = 0.001, dataMax = 0.002)
        assertTrue(maxY - minY >= 0.02 * 0.9, "at least ~min span with pad: ${maxY - minY}")
        assertTrue(minY <= 0.0 && maxY >= 0.0)
    }
}

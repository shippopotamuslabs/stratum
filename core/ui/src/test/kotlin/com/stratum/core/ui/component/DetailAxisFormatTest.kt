package com.stratum.core.ui.component

import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class DetailAxisFormatTest {
    @Test
    @DisplayName("progress axis collapses near-zero and avoids -0")
    fun progressNearZero() {
        assertEquals("0", formatChartNumber(0.0, DetailYScale.Progress))
        assertEquals("0", formatChartNumber(-1e-15, DetailYScale.Progress))
    }

    @Test
    @DisplayName("progress axis uses enough precision for sub-1 values")
    fun progressSmall() {
        assertEquals("0.12", formatChartNumber(0.12, DetailYScale.Progress))
        assertEquals("0.015", formatChartNumber(0.015, DetailYScale.Progress))
        assertEquals("0.0012", formatChartNumber(0.0012, DetailYScale.Progress))
    }

    @Test
    @DisplayName("weight and volume format sensibly")
    fun weightAndVolume() {
        assertEquals("225", formatChartNumber(225.0, DetailYScale.Weight))
        assertEquals("12", formatChartNumber(12.0, DetailYScale.Volume))
        assertEquals("7.5", formatChartNumber(7.5, DetailYScale.Volume))
    }

    @Test
    @DisplayName("week-start labels use UTC (no local Sunday slip)")
    fun weekStartUtcLabel() {
        // Monday 2026-01-05 00:00 UTC
        val monday = Instant.parse("2026-01-05T00:00:00Z")
        assertEquals("Jan 5", formatWeekStartLabel(monday, "MMM d"))
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

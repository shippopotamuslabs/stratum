package com.stratum.core.ui.component

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
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
    @DisplayName("dayStartToEpochDay uses local calendar day (east-of-UTC Tokyo)")
    fun dayStartEpochDayTokyo() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        // Wed 2026-07-22 00:00 JST = Tue 2026-07-21 15:00 UTC
        val wedLocalMidnight = LocalDate.of(2026, 7, 22).atStartOfDay(tokyo).toInstant()
        val localEpochDay = dayStartToEpochDay(wedLocalMidnight.toEpochMilli(), tokyo)
        val utcEpochDay = weekStartToEpochDay(wedLocalMidnight.toEpochMilli())
        assertEquals(LocalDate.of(2026, 7, 22).toEpochDay(), localEpochDay)
        assertEquals(LocalDate.of(2026, 7, 21).toEpochDay(), utcEpochDay)
        assertNotEquals(
            utcEpochDay,
            localEpochDay,
            "east-of-UTC local midnight must not use UTC epoch day",
        )
        assertEquals("Jul 22", formatDayStartLabel(wedLocalMidnight, tokyo, "MMM d"))
    }

    @Test
    @DisplayName("dayStartToEpochDay keeps Wed evening NY on Wednesday after UTC flip")
    fun dayStartEpochDayNewYorkEvening() {
        val newYork = ZoneId.of("America/New_York")
        val wedEvening = Instant.parse("2026-07-23T00:30:00Z") // Wed 20:30 EDT
        val dayStart = LocalDate.of(2026, 7, 22).atStartOfDay(newYork).toInstant()
        assertEquals(
            LocalDate.of(2026, 7, 22).toEpochDay(),
            dayStartToEpochDay(dayStart.toEpochMilli(), newYork),
        )
        assertEquals(
            LocalDate.of(2026, 7, 22),
            wedEvening.atZone(newYork).toLocalDate(),
        )
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

    @Test
    @DisplayName("weightYRange expands flat e1RM so axis length never hits 0")
    fun weightRangeFlatSeries() {
        val (minY, maxY) = weightYRange(dataMin = 100.0, dataMax = 100.0)
        assertTrue(maxY - minY > 0.0, "length must be positive: $minY..$maxY")
        assertTrue(minY <= 100.0 && maxY >= 100.0)
        assertTrue(minY >= 0.0, "positive e1RM keeps non-negative floor: minY=$minY")
    }

    @Test
    @DisplayName("weightYRange handles single-point and non-finite model bounds")
    fun weightRangeSingleAndNonFinite() {
        val single = weightYRange(dataMin = 80.0, dataMax = 80.0)
        assertTrue(single.second - single.first > 0.0)
        val nan = weightYRange(dataMin = Double.NaN, dataMax = Double.NaN)
        assertTrue(nan.first.isFinite() && nan.second.isFinite())
        assertTrue(nan.second - nan.first > 0.0)
    }

    @Test
    @DisplayName("volumeYRange floors at 0 and expands all-zero / flat hard sets")
    fun volumeRangeZeroAndFlat() {
        val zeros = volumeYRange(dataMin = 0.0, dataMax = 0.0)
        assertEquals(0.0, zeros.first, 0.0)
        assertTrue(zeros.second > 0.0, "all-zero week still needs positive ceiling")

        val flat = volumeYRange(dataMin = 4.0, dataMax = 4.0)
        assertEquals(0.0, flat.first, 0.0)
        assertTrue(flat.second > 4.0, "pad above peak sets: max=${flat.second}")
    }

    @Test
    @DisplayName("volumeYRange keeps positive span for normal multi-week volume")
    fun volumeRangeNormal() {
        val (minY, maxY) = volumeYRange(dataMin = 2.0, dataMax = 12.0)
        assertEquals(0.0, minY, 0.0)
        assertTrue(maxY >= 12.0)
        assertTrue(maxY - minY > 0.0)
    }

    @Test
    @DisplayName("finiteDataBounds never returns inverted or zero-length pair")
    fun finiteBoundsAlwaysUsable() {
        val inverted = finiteDataBounds(10.0, 2.0, fallbackSpan = 1.0)
        assertEquals(2.0, inverted.first, 0.0)
        assertEquals(10.0, inverted.second, 0.0)

        val flat = finiteDataBounds(5.0, 5.0, fallbackSpan = 2.0)
        assertEquals(2.0, flat.second - flat.first, 1e-9)
    }
}

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
    @DisplayName("hours stored as a decimal format as hours and minutes")
    fun hoursAsDuration() {
        assertEquals("0m", formatHoursAsDuration(0.0))
        assertEquals("48m", formatHoursAsDuration(0.8))
        assertEquals("1h 0m", formatHoursAsDuration(1.0))
        assertEquals("1h 35m", formatHoursAsDuration(95.0 / 60.0))
        assertEquals("7h 12m", formatHoursAsDuration(7.2))
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

    @Test
    @DisplayName("steps scale labels stay exact below 1k and compact above")
    fun stepsScaleLabels() {
        assertEquals("0", formatChartNumber(0.0, DetailYScale.Steps))
        assertEquals("500", formatChartNumber(500.0, DetailYScale.Steps))
        assertEquals("999", formatChartNumber(999.4, DetailYScale.Steps))
        assertEquals("1k", formatChartNumber(999.6, DetailYScale.Steps))
        assertEquals("1k", formatChartNumber(1000.0, DetailYScale.Steps))
        assertEquals("2.5k", formatChartNumber(2500.0, DetailYScale.Steps))
        assertEquals("5k", formatChartNumber(5000.0, DetailYScale.Steps))
        assertEquals("10k", formatChartNumber(10000.0, DetailYScale.Steps))
        assertEquals("12.5k", formatChartNumber(12500.0, DetailYScale.Steps))
    }

    @Test
    @DisplayName("stepsYRange floors at 0 with a nice ceiling above the peak")
    fun stepsRangeNiceCeiling() {
        val (minY, maxY) = stepsYRange(dataMin = 4800.0, dataMax = 9000.0)
        assertEquals(0.0, minY, 0.0)
        assertEquals(10000.0, maxY, 0.0)
    }

    @Test
    @DisplayName("stepsYRange plus labels yield 0/5k/10k for a typical week")
    fun stepsRangeTypicalWeekTicks() {
        // Peak 8,432 (preview week) → ceiling 10k, mid tick 5k.
        val (minY, maxY) = stepsYRange(dataMin = 4800.0, dataMax = 8432.0)
        assertEquals(0.0, minY, 0.0)
        assertEquals(10000.0, maxY, 0.0)
        assertEquals("0", formatChartNumber(minY, DetailYScale.Steps))
        assertEquals("5k", formatChartNumber((minY + maxY) / 2.0, DetailYScale.Steps))
        assertEquals("10k", formatChartNumber(maxY, DetailYScale.Steps))
    }

    @Test
    @DisplayName("stepsYRange keeps positive length for all-zero, flat, and non-finite weeks")
    fun stepsRangeDegenerate() {
        val zeros = stepsYRange(dataMin = 0.0, dataMax = 0.0)
        assertEquals(0.0, zeros.first, 0.0)
        assertTrue(zeros.second > 0.0, "all-zero week still needs positive ceiling")

        val flat = stepsYRange(dataMin = 4.0, dataMax = 4.0)
        assertEquals(0.0, flat.first, 0.0)
        assertTrue(flat.second > 4.0, "pad above tiny peaks: max=${flat.second}")

        val nan = stepsYRange(dataMin = Double.NaN, dataMax = Double.NaN)
        assertTrue(nan.first.isFinite() && nan.second.isFinite())
        assertTrue(nan.second - nan.first > 0.0)
    }

    @Test
    @DisplayName("body-weight values format as whole units on the weight axis")
    fun weightAxisBodyWeight() {
        assertEquals("176", formatChartNumber(176.4, DetailYScale.Weight))
        assertEquals("80.2", formatChartNumber(80.2, DetailYScale.Weight))
    }

    @Test
    @DisplayName("weightYRange fits body-weight weeks without forcing zero")
    fun weightRangeBodyWeightFitted() {
        val (minY, maxY) = weightYRange(dataMin = 176.4, dataMax = 178.2)
        assertTrue(minY > 100.0, "floor stays near the data, not at zero: minY=$minY")
        assertTrue(maxY < 200.0, "ceiling stays near the data: maxY=$maxY")
        assertTrue(minY <= 176.4 && maxY >= 178.2)
    }

    @Test
    @DisplayName("compact tick counts pair 3 labels with steps and 4 with other scales")
    fun compactTickCounts() {
        assertEquals(3, yTickCountFor(DetailYScale.Steps))
        assertEquals(4, yTickCountFor(DetailYScale.Weight))
        assertEquals(4, yTickCountFor(DetailYScale.Volume))
        assertEquals(4, yTickCountFor(DetailYScale.Progress))
        assertEquals(4, yTickCountFor(DetailYScale.Vitals))
    }

    @Test
    @DisplayName("STR-108: vitals axis labels are integers")
    fun vitalsAxisLabels() {
        assertEquals("52", formatChartNumber(52.0, DetailYScale.Vitals))
        assertEquals("48", formatChartNumber(48.0, DetailYScale.Vitals))
        assertEquals("52", formatChartNumber(52.4, DetailYScale.Vitals))
        assertEquals("48", formatChartNumber(47.6, DetailYScale.Vitals))
        assertEquals("0", formatChartNumber(0.0, DetailYScale.Vitals))
    }

    @Test
    @DisplayName("STR-108: vitalsYRange fits RHR weeks without forcing zero")
    fun vitalsRangeFitted() {
        val (minY, maxY) = vitalsYRange(dataMin = 51.0, dataMax = 55.0)
        assertTrue(minY > 40.0, "floor stays near the data, not at zero: minY=$minY")
        assertTrue(maxY < 60.0, "ceiling stays near the data: maxY=$maxY")
        assertTrue(minY <= 51.0 && maxY >= 55.0)
    }

    @Test
    @DisplayName("STR-108: vitalsYRange expands flat weeks so axis length never hits 0")
    fun vitalsRangeFlatSeries() {
        val (minY, maxY) = vitalsYRange(dataMin = 52.0, dataMax = 52.0)
        assertTrue(maxY - minY > 0.0, "length must be positive: $minY..$maxY")
        assertTrue(minY <= 52.0 && maxY >= 52.0)
        assertTrue(minY >= 0.0, "positive vitals keep non-negative floor: minY=$minY")
    }

    @Test
    @DisplayName("STR-108: vitalsYRange handles non-finite model bounds")
    fun vitalsRangeNonFinite() {
        val nan = vitalsYRange(dataMin = Double.NaN, dataMax = Double.NaN)
        assertTrue(nan.first.isFinite() && nan.second.isFinite())
        assertTrue(nan.second - nan.first > 0.0)
    }
}

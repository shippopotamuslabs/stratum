package com.stratum

import com.stratum.core.ui.component.TrendPointUi
import com.stratum.domain.healthconnect.DailyVitalSample
import com.stratum.domain.healthconnect.HealthMetricsPages
import com.stratum.domain.healthconnect.NightlySleepDuration
import com.stratum.domain.healthconnect.RecoveryMetrics
import com.stratum.domain.healthconnect.SleepNight
import com.stratum.domain.healthconnect.SleepZones
import com.stratum.domain.healthconnect.WeightMetrics
import com.stratum.domain.healthconnect.WeightSample
import com.stratum.domain.lift.Weight
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class DashboardHealthUiTest {

    @Test
    @DisplayName("step counts use grouping separators")
    fun formatSteps() {
        assertEquals("8,432", formatStepCount(8_432L))
        assertEquals("0", formatStepCount(0L))
    }

    @Test
    @DisplayName("sleep duration is hours and minutes")
    fun formatSleep() {
        assertEquals("7h 12m", formatHoursMinutes(Duration.ofHours(7).plusMinutes(12)))
        assertEquals("45m", formatHoursMinutes(Duration.ofMinutes(45)))
    }

    @Test
    @DisplayName("weight delta keeps a signed tabular amount")
    fun formatDelta() {
        assertEquals("+1.2 lb", formatWeightDelta(1.2, Weight.WeightUnit.Lb))
        assertEquals("−0.4 kg", formatWeightDelta(-0.4, Weight.WeightUnit.Kg))
    }

    @Test
    @DisplayName("short date uses a compact month-day form")
    fun formatDate() {
        val instant = Instant.parse("2026-08-12T12:00:00Z")
        assertEquals("Aug 12", formatShortDate(instant, ZoneOffset.UTC))
    }

    @Test
    @DisplayName("empty health pages do not claim a drawable chart")
    fun emptyPagesHaveNoChart() {
        assertFalse(DashboardHealthUiState(visible = true).hasChart())
        assertFalse(
            ActivityHealthPageUi(
                dailyPoints = listOf(TrendPointUi(0, 0f), TrendPointUi(1, 0f)),
            ).hasChart(),
        )
    }

    @Test
    @DisplayName("a page with samples claims a drawable chart")
    fun filledPagesHaveChart() {
        assertTrue(
            ActivityHealthPageUi(
                dailyPoints = listOf(TrendPointUi(0, 0f), TrendPointUi(1, 1200f)),
            ).hasChart(),
        )
        assertTrue(
            WeightHealthPageUi(
                trendPoints = listOf(TrendPointUi(0, 176f), TrendPointUi(1, 175.5f)),
            ).hasChart(),
        )
        assertTrue(
            RecoveryHealthPageUi(
                nightlyPoints = listOf(TrendPointUi(0, 0f), TrendPointUi(1, 7.2f)),
            ).hasChart(),
        )
        assertTrue(
            RecoveryHealthPageUi(
                restingHrDailyPoints = listOf(TrendPointUi(0, 62f), TrendPointUi(1, 61f)),
            ).hasChart(),
        )
        assertTrue(
            RecoveryHealthPageUi(
                hrvDailyPoints = listOf(TrendPointUi(0, 48f), TrendPointUi(1, 47f)),
            ).hasChart(),
        )
    }

    @Test
    @DisplayName("recovery UI maps last-night stage durations and nightly stage hours")
    fun recoveryMapsStageDurationsAndNightlySeries() {
        val zone = ZoneOffset.UTC
        val dayStart = Instant.parse("2026-08-12T00:00:00Z")
        val pages = HealthMetricsPages(
            visible = true,
            recovery = RecoveryMetrics(
                lastNight = SleepNight(
                    start = Instant.parse("2026-08-11T22:30:00Z"),
                    end = Instant.parse("2026-08-12T06:42:00Z"),
                    duration = Duration.ofHours(7).plusMinutes(12),
                    zones = SleepZones(
                        light = Duration.ofHours(3).plusMinutes(14),
                        deep = Duration.ofHours(1).plusMinutes(35),
                        rem = Duration.ofHours(1).plusMinutes(39),
                        awake = Duration.ofMinutes(43),
                    ),
                ),
                nightlyDurations = listOf(
                    NightlySleepDuration(
                        dayStart = dayStart,
                        duration = Duration.ofHours(7).plusMinutes(12),
                        zones = SleepZones(
                            light = Duration.ofHours(3),
                            deep = Duration.ofHours(2),
                            rem = Duration.ofHours(2),
                            awake = Duration.ofMinutes(48),
                        ),
                    ),
                ),
            ),
        )

        val ui = pages.toHealthUi(Weight.WeightUnit.Lb, zone)

        assertEquals("7h 12m", ui.recovery.lastNightDurationText)
        assertEquals("7h 12m", ui.recovery.nightlyAverageText)
        assertEquals(3f, ui.recovery.lightNightlyPoints.single().value)
        assertEquals(2f, ui.recovery.deepNightlyPoints.single().value)
        assertEquals(2f, ui.recovery.remNightlyPoints.single().value)
        assertEquals(0.8f, ui.recovery.awakeNightlyPoints.single().value)
        assertTrue(ui.recovery.hasChart())
    }

    @Test
    @DisplayName("STR-96: missing last night with window history never shows the no-sleep copy")
    fun missingLastNightWithHistoryHidesSleepEmptyText() {
        val zone = ZoneOffset.UTC
        val pages = HealthMetricsPages(
            visible = true,
            recovery = RecoveryMetrics(
                lastNight = null,
                nightlyDurations = listOf(
                    NightlySleepDuration(
                        dayStart = Instant.parse("2026-08-11T00:00:00Z"),
                        duration = Duration.ofHours(6).plusMinutes(48),
                        zones = null,
                    ),
                    NightlySleepDuration(
                        dayStart = Instant.parse("2026-08-12T00:00:00Z"),
                        duration = Duration.ofHours(7).plusMinutes(5),
                        zones = null,
                    ),
                ),
            ),
        )

        val ui = pages.toHealthUi(Weight.WeightUnit.Lb, zone)

        assertEquals(null, ui.recovery.lastNightDurationText)
        assertTrue(ui.recovery.hasChart())
        assertFalse(ui.recovery.showSleepEmptyText())
    }

    @Test
    @DisplayName("STR-96: window with no sleep data at all shows the no-sleep copy")
    fun emptyWindowShowsSleepEmptyText() {
        val zone = ZoneOffset.UTC
        val pages = HealthMetricsPages(
            visible = true,
            recovery = RecoveryMetrics(
                lastNight = null,
                nightlyDurations = emptyList(),
            ),
        )

        val ui = pages.toHealthUi(Weight.WeightUnit.Lb, zone)

        assertEquals(null, ui.recovery.lastNightDurationText)
        assertFalse(ui.recovery.hasChart())
        assertTrue(ui.recovery.showSleepEmptyText())
    }

    @Test
    @DisplayName("STR-96: last-night hero suppresses the no-sleep copy")
    fun lastNightHeroHidesSleepEmptyText() {
        assertFalse(
            RecoveryHealthPageUi(
                lastNightDurationText = "7h 12m",
            ).showSleepEmptyText(),
        )
        assertFalse(
            RecoveryHealthPageUi(
                lastNightDurationText = null,
                nightlyPoints = listOf(TrendPointUi(0, 0f), TrendPointUi(1, 7.2f)),
            ).showSleepEmptyText(),
        )
        assertTrue(
            RecoveryHealthPageUi(
                lastNightDurationText = null,
                nightlyPoints = listOf(TrendPointUi(0, 0f), TrendPointUi(1, 0f)),
            ).showSleepEmptyText(),
        )
    }

    @Test
    @DisplayName("STR-98: recovery UI maps sparse per-day vitals and their averages")
    fun recoveryMapsVitalTrendPoints() {
        val zone = ZoneOffset.UTC
        val pages = HealthMetricsPages(
            visible = true,
            recovery = RecoveryMetrics(
                restingHeartRateDaily = listOf(
                    DailyVitalSample(Instant.parse("2026-08-10T00:00:00Z"), null),
                    DailyVitalSample(Instant.parse("2026-08-11T00:00:00Z"), 62.0),
                    DailyVitalSample(Instant.parse("2026-08-12T00:00:00Z"), 60.0),
                ),
                hrvDaily = listOf(
                    DailyVitalSample(Instant.parse("2026-08-10T00:00:00Z"), null),
                    DailyVitalSample(Instant.parse("2026-08-11T00:00:00Z"), 44.0),
                    DailyVitalSample(Instant.parse("2026-08-12T00:00:00Z"), 48.0),
                ),
            ),
        )

        val ui = pages.toHealthUi(Weight.WeightUnit.Lb, zone)

        // Null days are dropped; weekIndex keeps survivors aligned with the 7-day labels.
        assertEquals(
            listOf(TrendPointUi(1, 62f), TrendPointUi(2, 60f)),
            ui.recovery.restingHrDailyPoints,
        )
        assertEquals(
            listOf(TrendPointUi(1, 44f), TrendPointUi(2, 48f)),
            ui.recovery.hrvDailyPoints,
        )
        assertEquals("46 ms", ui.recovery.hrvAverageText)
        assertTrue(ui.recovery.hasChart())
    }

    @Test
    @DisplayName("weight UI maps sample weekdays for the trend axis")
    fun weightMapsDayLabelsFromSampleTimes() {
        val zone = ZoneOffset.UTC
        val pages = HealthMetricsPages(
            visible = true,
            weight = WeightMetrics(
                latest = WeightSample(
                    time = Instant.parse("2026-08-12T12:00:00Z"),
                    weight = Weight(176.4, Weight.WeightUnit.Lb),
                ),
                sevenDayMean = Weight(177.1, Weight.WeightUnit.Lb),
                samples = listOf(
                    WeightSample(
                        time = Instant.parse("2026-08-11T12:00:00Z"),
                        weight = Weight(177.0, Weight.WeightUnit.Lb),
                    ),
                    WeightSample(
                        time = Instant.parse("2026-08-12T12:00:00Z"),
                        weight = Weight(176.4, Weight.WeightUnit.Lb),
                    ),
                ),
            ),
        )

        val ui = pages.toHealthUi(Weight.WeightUnit.Lb, zone)

        assertEquals(listOf("T", "W"), ui.weight.dayLabels)
        assertEquals(2, ui.weight.trendPoints.size)
        assertTrue(ui.weight.hasChart())
    }

    @Test
    @DisplayName("STR-107: weight UI maps the trailing-mean series aligned with raw points")
    fun weightMapsTrendMeanPoints() {
        val zone = ZoneOffset.UTC
        val pages = HealthMetricsPages(
            visible = true,
            weight = WeightMetrics(
                samples = listOf(
                    WeightSample(
                        time = Instant.parse("2026-08-11T12:00:00Z"),
                        weight = Weight(177.0, Weight.WeightUnit.Lb),
                    ),
                    WeightSample(
                        time = Instant.parse("2026-08-12T12:00:00Z"),
                        weight = Weight(176.4, Weight.WeightUnit.Lb),
                    ),
                ),
                threeDayTrend = listOf(
                    WeightSample(
                        time = Instant.parse("2026-08-11T12:00:00Z"),
                        weight = Weight(177.0, Weight.WeightUnit.Lb),
                    ),
                    WeightSample(
                        time = Instant.parse("2026-08-12T12:00:00Z"),
                        weight = Weight(176.7, Weight.WeightUnit.Lb),
                    ),
                ),
            ),
        )

        val ui = pages.toHealthUi(Weight.WeightUnit.Lb, zone)

        assertEquals(
            listOf(TrendPointUi(0, 177.0f), TrendPointUi(1, 176.7f)),
            ui.weight.trendMeanPoints,
        )
        assertEquals(
            ui.weight.trendPoints.map { it.weekIndex },
            ui.weight.trendMeanPoints.map { it.weekIndex },
        )
    }

    @Test
    @DisplayName("STR-107: weight UI leaves the trailing-mean series empty when unknown")
    fun weightMapsEmptyTrendMeanPoints() {
        val zone = ZoneOffset.UTC
        val pages = HealthMetricsPages(
            visible = true,
            weight = WeightMetrics(
                samples = listOf(
                    WeightSample(
                        time = Instant.parse("2026-08-12T12:00:00Z"),
                        weight = Weight(176.4, Weight.WeightUnit.Lb),
                    ),
                ),
            ),
        )

        val ui = pages.toHealthUi(Weight.WeightUnit.Lb, zone)

        assertTrue(ui.weight.trendMeanPoints.isEmpty())
    }

    @Test
    @DisplayName("STR-111: weight legend shows only when both series are drawable")
    fun weightLegendGating() {
        val raw = listOf(TrendPointUi(0, 177f), TrendPointUi(1, 176.4f))
        val trend = listOf(TrendPointUi(0, 177f), TrendPointUi(1, 176.7f))
        assertTrue(WeightHealthPageUi(trendPoints = raw, trendMeanPoints = trend).hasTrendLegend())
        // Raw chart without a drawable trend: chart yes, legend no.
        val rawOnly = WeightHealthPageUi(trendPoints = raw, trendMeanPoints = emptyList())
        assertTrue(rawOnly.hasChart())
        assertFalse(rawOnly.hasTrendLegend())
        // Single sample: neither chart nor legend.
        val single = WeightHealthPageUi(
            trendPoints = listOf(TrendPointUi(0, 177f)),
            trendMeanPoints = listOf(TrendPointUi(0, 177f)),
        )
        assertFalse(single.hasChart())
        assertFalse(single.hasTrendLegend())
    }
}

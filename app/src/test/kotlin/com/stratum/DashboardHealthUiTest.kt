package com.stratum

import com.stratum.core.ui.component.TrendPointUi
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
                stageSlices = listOf(
                    SleepStageSliceUi(SleepStageUi.Light, 0.5f, "3h"),
                    SleepStageSliceUi(SleepStageUi.Deep, 0.5f, "3h"),
                ),
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
        assertEquals("3h 14m", ui.recovery.stageSlices.first { it.stage == SleepStageUi.Light }.durationText)
        assertEquals("1h 35m", ui.recovery.stageSlices.first { it.stage == SleepStageUi.Deep }.durationText)
        assertEquals("1h 39m", ui.recovery.stageSlices.first { it.stage == SleepStageUi.Rem }.durationText)
        assertEquals("43m", ui.recovery.stageSlices.first { it.stage == SleepStageUi.Awake }.durationText)
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
        assertTrue(ui.recovery.stageSlices.isEmpty())
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
}

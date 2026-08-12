package com.stratum.domain.healthconnect

import com.stratum.core.testing.healthconnect.FakeHealthConnectMetricsPort
import com.stratum.core.testing.healthconnect.FakeHealthConnectPreferencesRepository
import com.stratum.domain.lift.Weight
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LoadHealthConnectMetricsUseCaseTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 8, 12)
    private val now: Instant = today.atTime(15, 0).toInstant(zone)

    private lateinit var prefs: FakeHealthConnectPreferencesRepository
    private lateinit var metrics: FakeHealthConnectMetricsPort
    private lateinit var load: LoadHealthConnectMetricsUseCase

    @BeforeEach
    fun setup() {
        prefs = FakeHealthConnectPreferencesRepository()
        metrics = FakeHealthConnectMetricsPort()
        load = LoadHealthConnectMetricsUseCase(metrics, prefs)
    }

    @Test
    @DisplayName("hidden when Health Connect is disabled")
    fun hiddenWhenDisabled() = runTest {
        prefs.setPreferences(HealthConnectPreferences(enabled = false))
        metrics.granted = setOf(HealthMetricType.Steps)

        val pages = load(zone, now)

        assertFalse(pages.visible)
    }

    @Test
    @DisplayName("hidden when SDK is unavailable")
    fun hiddenWhenUnavailable() = runTest {
        prefs.setPreferences(HealthConnectPreferences(enabled = true))
        metrics.available = false
        metrics.granted = setOf(HealthMetricType.Steps)

        val pages = load(zone, now)

        assertFalse(pages.visible)
    }

    @Test
    @DisplayName("hidden when no read types are granted")
    fun hiddenWhenNoReads() = runTest {
        prefs.setPreferences(HealthConnectPreferences(enabled = true))
        metrics.granted = emptySet()

        val pages = load(zone, now)

        assertFalse(pages.visible)
    }

    @Test
    @DisplayName("visible with 7 zero-filled step days when steps are granted")
    fun activityFillsSevenDays() = runTest {
        prefs.setPreferences(HealthConnectPreferences(enabled = true))
        metrics.granted = setOf(HealthMetricType.Steps)
        val todayStart = today.atStartOfDay().toInstant(zone)
        metrics.snapshot = HealthMetricsSnapshot(
            dailySteps = listOf(DailyStepCount(todayStart, 8_432L)),
        )

        val pages = load(zone, now)

        assertTrue(pages.visible)
        assertEquals(HEALTH_METRICS_DAY_COUNT, pages.activity.dailyCounts.size)
        assertEquals(8_432L, pages.activity.todaySteps)
        assertEquals(8_432.0, pages.activity.sevenDayAverage)
        assertEquals(0L, pages.activity.dailyCounts.first().count)
        assertEquals(todayStart, metrics.lastRequest?.todayStart)
    }

    @Test
    @DisplayName("7-day step average ignores zero days")
    fun stepAverageIgnoresZeros() = runTest {
        prefs.setPreferences(HealthConnectPreferences(enabled = true))
        metrics.granted = setOf(HealthMetricType.Steps)
        val d0 = today.minusDays(6).atStartOfDay().toInstant(zone)
        val d6 = today.atStartOfDay().toInstant(zone)
        metrics.snapshot = HealthMetricsSnapshot(
            dailySteps = listOf(
                DailyStepCount(d0, 4_000L),
                DailyStepCount(d6, 8_000L),
            ),
        )

        val pages = load(zone, now)

        assertEquals(6_000.0, pages.activity.sevenDayAverage)
    }

    @Test
    @DisplayName("weight latest and 7-day mean use samples in the window")
    fun weightLatestAndMean() = runTest {
        prefs.setPreferences(HealthConnectPreferences(enabled = true))
        metrics.granted = setOf(HealthMetricType.Weight)
        val older = WeightSample(today.minusDays(3).atTime(8, 0).toInstant(zone), kg(80.0))
        val latest = WeightSample(today.atTime(7, 0).toInstant(zone), kg(81.0))
        metrics.snapshot = HealthMetricsSnapshot(weightSamples = listOf(older, latest))

        val pages = load(zone, now)

        assertEquals(81.0, pages.weight.latest?.weight?.amount)
        assertEquals(80.5, pages.weight.sevenDayMean?.amount)
        assertEquals(2, pages.weight.samples.size)
    }

    @Test
    @DisplayName("last night is the longest session ending in the 36-hour lookback")
    fun lastNightPick() = runTest {
        prefs.setPreferences(HealthConnectPreferences(enabled = true))
        metrics.granted = setOf(HealthMetricType.Sleep)
        val older = SleepSessionSample(
            start = today.minusDays(2).atTime(22, 0).toInstant(zone),
            end = today.minusDays(1).atTime(6, 0).toInstant(zone),
        )
        val lastNight = SleepSessionSample(
            start = today.minusDays(1).atTime(22, 30).toInstant(zone),
            end = today.atTime(6, 45).toInstant(zone),
            stages = listOf(
                SleepStageSample(
                    start = today.minusDays(1).atTime(23, 0).toInstant(zone),
                    end = today.atTime(1, 0).toInstant(zone),
                    stage = SleepStageType.Deep,
                ),
                SleepStageSample(
                    start = today.atTime(1, 0).toInstant(zone),
                    end = today.atTime(2, 0).toInstant(zone),
                    stage = SleepStageType.Rem,
                ),
            ),
        )
        val nap = SleepSessionSample(
            start = today.atTime(14, 0).toInstant(zone),
            end = today.atTime(14, 20).toInstant(zone),
        )
        metrics.snapshot = HealthMetricsSnapshot(sleepSessions = listOf(older, lastNight, nap))

        val pages = load(zone, now)

        assertEquals(lastNight.end, pages.recovery.lastNight?.end)
        assertNotNull(pages.recovery.lastNight?.zones)
        assertEquals(Duration.ofHours(2), pages.recovery.lastNight?.zones?.deep)
        assertEquals(Duration.ofHours(1), pages.recovery.lastNight?.zones?.rem)
        assertEquals(HEALTH_METRICS_DAY_COUNT, pages.recovery.nightlyDurations.size)
    }

    @Test
    @DisplayName("RHR and HRV hide when those types are not granted")
    fun vitalsHiddenWhenNotGranted() = runTest {
        prefs.setPreferences(HealthConnectPreferences(enabled = true))
        metrics.granted = setOf(HealthMetricType.Sleep)
        metrics.snapshot = HealthMetricsSnapshot(
            restingHeartRateSamples = listOf(
                HeartRateSample(today.atTime(7, 0).toInstant(zone), 52),
            ),
            hrvSamples = listOf(
                HrvSample(today.atTime(7, 0).toInstant(zone), 48.0),
            ),
        )

        val pages = load(zone, now)

        assertNull(pages.recovery.restingHeartRateBpm)
        assertNull(pages.recovery.hrvRmssdMillis)
    }

    @Test
    @DisplayName("RHR latest vs 7-day mean and latest HRV when granted")
    fun rhrAndHrvWhenGranted() = runTest {
        prefs.setPreferences(HealthConnectPreferences(enabled = true))
        metrics.granted = setOf(HealthMetricType.RestingHeartRate, HealthMetricType.HeartRateVariability)
        metrics.snapshot = HealthMetricsSnapshot(
            restingHeartRateSamples = listOf(
                HeartRateSample(today.minusDays(2).atTime(7, 0).toInstant(zone), 50),
                HeartRateSample(today.atTime(7, 0).toInstant(zone), 54),
            ),
            hrvSamples = listOf(
                HrvSample(today.minusDays(1).atTime(7, 0).toInstant(zone), 40.0),
                HrvSample(today.atTime(7, 0).toInstant(zone), 45.5),
            ),
        )

        val pages = load(zone, now)

        assertEquals(54L, pages.recovery.restingHeartRateBpm)
        assertEquals(52.0, pages.recovery.restingHeartRateSevenDayMean)
        assertEquals(45.5, pages.recovery.hrvRmssdMillis)
    }

    @Test
    @DisplayName("generic sleep stages do not produce a zone bar")
    fun genericStagesSkipZones() {
        val stages = listOf(
            SleepStageSample(
                start = Instant.parse("2026-08-12T02:00:00Z"),
                end = Instant.parse("2026-08-12T06:00:00Z"),
                stage = SleepStageType.Sleeping,
            ),
        )
        assertNull(stages.toSleepZones())
    }

    private fun kg(amount: Double): Weight = Weight(amount, Weight.WeightUnit.Kg)
}

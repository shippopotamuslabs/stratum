package com.stratum.application.healthconnect

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
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import com.stratum.domain.health.DailyStepCount
import com.stratum.domain.health.HEALTH_METRICS_DAY_COUNT
import com.stratum.domain.healthconnect.HealthConnectDiagnostic
import com.stratum.domain.healthconnect.HealthConnectPreferences
import com.stratum.domain.health.HealthMetricType
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.health.HealthMetricsRequest
import com.stratum.domain.health.HealthMetricsSnapshot
import com.stratum.domain.health.HeartRateSample
import com.stratum.domain.health.HrvSample
import com.stratum.domain.health.SleepSessionSample
import com.stratum.domain.health.SleepStageSample
import com.stratum.domain.health.SleepStageType
import com.stratum.domain.health.VitalRange
import com.stratum.domain.health.WeightSample

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
    @DisplayName("STR-107: daily weigh-ins smooth a spike with a trailing lag")
    fun weightTrendSmoothsDailySpike() = runTest {
        prefs.setPreferences(HealthConnectPreferences(enabled = true))
        metrics.granted = setOf(HealthMetricType.Weight)
        metrics.snapshot = HealthMetricsSnapshot(
            weightSamples = listOf(
                WeightSample(today.minusDays(3).atTime(8, 0).toInstant(zone), kg(80.0)),
                WeightSample(today.minusDays(2).atTime(8, 0).toInstant(zone), kg(80.0)),
                WeightSample(today.minusDays(1).atTime(8, 0).toInstant(zone), kg(83.0)),
                WeightSample(today.atTime(7, 0).toInstant(zone), kg(80.0)),
            ),
        )

        val pages = load(zone, now)

        val trend = pages.weight.threeDayTrend
        assertEquals(4, trend.size)
        assertEquals(listOf(80.0, 80.0, 81.0, 80.75), trend.map { it.weight.amount })
        assertEquals(
            pages.weight.samples.map { it.time },
            trend.map { it.time },
        )
    }

    @Test
    @DisplayName("STR-107: sparse weeks hug the raw series")
    fun weightTrendHugsSparseSamples() = runTest {
        prefs.setPreferences(HealthConnectPreferences(enabled = true))
        metrics.granted = setOf(HealthMetricType.Weight)
        metrics.snapshot = HealthMetricsSnapshot(
            weightSamples = listOf(
                WeightSample(today.minusDays(6).atTime(8, 0).toInstant(zone), kg(80.0)),
                WeightSample(today.atTime(7, 0).toInstant(zone), kg(81.0)),
            ),
        )

        val pages = load(zone, now)

        assertEquals(
            pages.weight.samples.map { it.weight.amount },
            pages.weight.threeDayTrend.map { it.weight.amount },
        )
    }

    @Test
    @DisplayName("STR-107: trend window is time-aware, not last-N samples")
    fun weightTrendIgnoresSamplesOlderThanThreeDays() = runTest {
        prefs.setPreferences(HealthConnectPreferences(enabled = true))
        metrics.granted = setOf(HealthMetricType.Weight)
        metrics.snapshot = HealthMetricsSnapshot(
            weightSamples = listOf(
                WeightSample(today.minusDays(6).atTime(8, 0).toInstant(zone), kg(70.0)),
                WeightSample(today.atTime(7, 0).toInstant(zone), kg(84.0)),
                WeightSample(today.atTime(8, 0).toInstant(zone), kg(86.0)),
            ),
        )

        val pages = load(zone, now)

        // A last-3 mean would drag today toward 70 kg; the 3-day window keeps only same-day samples.
        assertEquals(listOf(70.0, 84.0, 85.0), pages.weight.threeDayTrend.map { it.weight.amount })
    }

    @Test
    @DisplayName("STR-107: trend is empty when weight is not granted")
    fun weightTrendEmptyWhenNotGranted() = runTest {
        prefs.setPreferences(HealthConnectPreferences(enabled = true))
        metrics.granted = setOf(HealthMetricType.Steps)
        metrics.snapshot = HealthMetricsSnapshot(
            weightSamples = listOf(
                WeightSample(today.atTime(7, 0).toInstant(zone), kg(80.0)),
            ),
        )

        val pages = load(zone, now)

        assertTrue(pages.weight.threeDayTrend.isEmpty())
    }

    @Test
    @DisplayName("last night matches today's chart bucket, not a longer prior night")
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

        val tonight = pages.recovery.nightlyDurations.last()
        assertEquals(lastNight.end, pages.recovery.lastNight?.end)
        assertEquals(lastNight.start, pages.recovery.lastNight?.start)
        assertEquals(lastNight.duration, pages.recovery.lastNight?.duration)
        assertEquals(tonight.duration, pages.recovery.lastNight?.duration)
        assertEquals(tonight.zones, pages.recovery.lastNight?.zones)
        assertEquals(Duration.ofHours(2), pages.recovery.lastNight?.zones?.deep)
        assertEquals(Duration.ofHours(1), pages.recovery.lastNight?.zones?.rem)
        assertEquals(HEALTH_METRICS_DAY_COUNT, pages.recovery.nightlyDurations.size)
    }

    @Test
    @DisplayName("a longer Thursday night does not replace Friday as last night")
    fun lastNightPrefersMostRecentDayOverLongerPrior() = runTest {
        prefs.setPreferences(HealthConnectPreferences(enabled = true))
        metrics.granted = setOf(HealthMetricType.Sleep)
        val thursday = SleepSessionSample(
            start = today.minusDays(2).atTime(21, 0).toInstant(zone),
            end = today.minusDays(1).atTime(7, 0).toInstant(zone),
            stages = listOf(
                SleepStageSample(
                    start = today.minusDays(2).atTime(21, 0).toInstant(zone),
                    end = today.minusDays(1).atTime(2, 0).toInstant(zone),
                    stage = SleepStageType.Light,
                ),
                SleepStageSample(
                    start = today.minusDays(1).atTime(2, 0).toInstant(zone),
                    end = today.minusDays(1).atTime(7, 0).toInstant(zone),
                    stage = SleepStageType.Deep,
                ),
            ),
        )
        val friday = SleepSessionSample(
            start = today.minusDays(1).atTime(23, 0).toInstant(zone),
            end = today.atTime(6, 30).toInstant(zone),
            stages = listOf(
                SleepStageSample(
                    start = today.minusDays(1).atTime(23, 0).toInstant(zone),
                    end = today.atTime(3, 28).toInstant(zone),
                    stage = SleepStageType.Light,
                ),
                SleepStageSample(
                    start = today.atTime(3, 28).toInstant(zone),
                    end = today.atTime(5, 8).toInstant(zone),
                    stage = SleepStageType.Deep,
                ),
                SleepStageSample(
                    start = today.atTime(5, 8).toInstant(zone),
                    end = today.atTime(6, 30).toInstant(zone),
                    stage = SleepStageType.Rem,
                ),
            ),
        )
        metrics.snapshot = HealthMetricsSnapshot(sleepSessions = listOf(thursday, friday))

        val pages = load(zone, now)

        val fridayBucket = pages.recovery.nightlyDurations.last()
        assertEquals(friday.end, pages.recovery.lastNight?.end)
        assertEquals(fridayBucket.zones, pages.recovery.lastNight?.zones)
        assertEquals(Duration.ofHours(4).plusMinutes(28), pages.recovery.lastNight?.zones?.light)
        assertEquals(Duration.ofHours(1).plusMinutes(40), pages.recovery.lastNight?.zones?.deep)
        assertEquals(Duration.ofHours(1).plusMinutes(22), pages.recovery.lastNight?.zones?.rem)
        assertTrue(thursday.duration > friday.duration)
    }

    @Test
    @DisplayName("a shorter leftover session on the same day is not added to the overnight")
    fun nightlySleepKeepsLongestBlockNotLeftovers() {
        val request = HealthMetricsRequest(zone = zone, now = now)
        val night = SleepSessionSample(
            start = today.minusDays(1).atTime(22, 0).toInstant(zone),
            end = today.atTime(6, 0).toInstant(zone),
            originPackage = WATCH_ORIGIN,
            stages = listOf(
                SleepStageSample(
                    start = today.minusDays(1).atTime(22, 30).toInstant(zone),
                    end = today.atTime(1, 30).toInstant(zone),
                    stage = SleepStageType.Light,
                ),
                SleepStageSample(
                    start = today.atTime(1, 30).toInstant(zone),
                    end = today.atTime(3, 30).toInstant(zone),
                    stage = SleepStageType.Deep,
                ),
            ),
        )
        val leftover = SleepSessionSample(
            start = today.atTime(6, 20).toInstant(zone),
            end = today.atTime(7, 45).toInstant(zone),
            originPackage = FIT_ORIGIN,
            stages = listOf(
                SleepStageSample(
                    start = today.atTime(6, 20).toInstant(zone),
                    end = today.atTime(7, 0).toInstant(zone),
                    stage = SleepStageType.Rem,
                ),
                SleepStageSample(
                    start = today.atTime(7, 0).toInstant(zone),
                    end = today.atTime(7, 45).toInstant(zone),
                    stage = SleepStageType.Awake,
                ),
            ),
        )

        val nightly = fillNightlySleep(listOf(night, leftover), request)

        assertEquals(HEALTH_METRICS_DAY_COUNT, nightly.size)
        val todayNight = nightly.last()
        assertEquals(Duration.ofHours(8), todayNight.duration)
        assertEquals(Duration.ofHours(3), todayNight.zones?.light)
        assertEquals(Duration.ofHours(2), todayNight.zones?.deep)
        assertEquals(Duration.ZERO, todayNight.zones?.rem)
        assertEquals(Duration.ZERO, todayNight.zones?.awake)
        assertTrue(nightly.dropLast(1).all { it.zones == null && it.duration.isZero })
    }

    @Test
    @DisplayName("overlapping duplicate sleep sessions are not summed")
    fun overlappingSleepSessionsNotDoubleCounted() {
        val request = HealthMetricsRequest(zone = zone, now = now)
        val staged = SleepSessionSample(
            start = today.minusDays(1).atTime(22, 0).toInstant(zone),
            end = today.atTime(6, 0).toInstant(zone),
            originPackage = WATCH_ORIGIN,
            stages = listOf(
                SleepStageSample(
                    start = today.minusDays(1).atTime(22, 0).toInstant(zone),
                    end = today.atTime(6, 0).toInstant(zone),
                    stage = SleepStageType.Light,
                ),
            ),
        )
        val duplicate = SleepSessionSample(
            start = today.minusDays(1).atTime(22, 30).toInstant(zone),
            end = today.atTime(6, 30).toInstant(zone),
            originPackage = FIT_ORIGIN,
        )

        val nightly = fillNightlySleep(listOf(staged, duplicate), request)

        val todayNight = nightly.last()
        assertEquals(Duration.ofHours(8), todayNight.duration)
        assertEquals(Duration.ofHours(8), todayNight.zones?.light)
    }

    @Test
    @DisplayName("a staged session wins over a longer unstaged overlap")
    fun overlappingPrefersStagedSession() {
        val request = HealthMetricsRequest(zone = zone, now = now)
        val unstagedLong = SleepSessionSample(
            start = today.minusDays(1).atTime(21, 0).toInstant(zone),
            end = today.atTime(8, 0).toInstant(zone),
            originPackage = FIT_ORIGIN,
        )
        val staged = SleepSessionSample(
            start = today.minusDays(1).atTime(22, 0).toInstant(zone),
            end = today.atTime(6, 0).toInstant(zone),
            originPackage = WATCH_ORIGIN,
            stages = listOf(
                SleepStageSample(
                    start = today.minusDays(1).atTime(22, 0).toInstant(zone),
                    end = today.atTime(3, 0).toInstant(zone),
                    stage = SleepStageType.Light,
                ),
                SleepStageSample(
                    start = today.atTime(3, 0).toInstant(zone),
                    end = today.atTime(6, 0).toInstant(zone),
                    stage = SleepStageType.Deep,
                ),
            ),
        )

        val nightly = fillNightlySleep(listOf(unstagedLong, staged), request)

        val todayNight = nightly.last()
        assertEquals(Duration.ofHours(8), todayNight.duration)
        assertEquals(Duration.ofHours(5), todayNight.zones?.light)
        assertEquals(Duration.ofHours(3), todayNight.zones?.deep)
        assertTrue(unstagedLong.duration > staged.duration)
    }

    @Test
    @DisplayName("same-origin contiguous fragments merge into one overnight")
    fun sameOriginContiguousFragmentsMerge() {
        val request = HealthMetricsRequest(zone = zone, now = now)
        val firstHalf = SleepSessionSample(
            start = today.minusDays(1).atTime(22, 0).toInstant(zone),
            end = today.atTime(2, 0).toInstant(zone),
            originPackage = WATCH_ORIGIN,
            stages = listOf(
                SleepStageSample(
                    start = today.minusDays(1).atTime(22, 0).toInstant(zone),
                    end = today.atTime(2, 0).toInstant(zone),
                    stage = SleepStageType.Light,
                ),
            ),
        )
        val secondHalf = SleepSessionSample(
            start = today.atTime(2, 0).toInstant(zone),
            end = today.atTime(6, 0).toInstant(zone),
            originPackage = WATCH_ORIGIN,
            stages = listOf(
                SleepStageSample(
                    start = today.atTime(2, 0).toInstant(zone),
                    end = today.atTime(6, 0).toInstant(zone),
                    stage = SleepStageType.Deep,
                ),
            ),
        )

        val nightly = fillNightlySleep(listOf(firstHalf, secondHalf), request)

        val todayNight = nightly.last()
        assertEquals(Duration.ofHours(8), todayNight.duration)
        assertEquals(Duration.ofHours(4), todayNight.zones?.light)
        assertEquals(Duration.ofHours(4), todayNight.zones?.deep)
    }

    @Test
    @DisplayName("blank origin does not glue a morning leftover onto the overnight")
    fun blankOriginDoesNotGlueMorningLeftover() {
        val request = HealthMetricsRequest(zone = zone, now = now)
        val night = SleepSessionSample(
            start = today.minusDays(1).atTime(22, 0).toInstant(zone),
            end = today.atTime(6, 0).toInstant(zone),
            stages = listOf(
                SleepStageSample(
                    start = today.minusDays(1).atTime(22, 0).toInstant(zone),
                    end = today.atTime(6, 0).toInstant(zone),
                    stage = SleepStageType.Light,
                ),
            ),
        )
        val leftover = SleepSessionSample(
            start = today.atTime(6, 20).toInstant(zone),
            end = today.atTime(7, 45).toInstant(zone),
            stages = listOf(
                SleepStageSample(
                    start = today.atTime(6, 20).toInstant(zone),
                    end = today.atTime(7, 45).toInstant(zone),
                    stage = SleepStageType.Rem,
                ),
            ),
        )

        val nightly = fillNightlySleep(listOf(night, leftover), request)

        assertEquals(Duration.ofHours(8), nightly.last().duration)
        assertEquals(Duration.ofHours(8), nightly.last().zones?.light)
        assertEquals(Duration.ZERO, nightly.last().zones?.rem)
    }

    @Test
    @DisplayName("same-origin overlapping duplicates use the staged session duration")
    fun sameOriginOverlappingDuplicatesUsePreferredDuration() {
        val request = HealthMetricsRequest(zone = zone, now = now)
        val staged = SleepSessionSample(
            start = today.minusDays(1).atTime(22, 0).toInstant(zone),
            end = today.atTime(6, 0).toInstant(zone),
            originPackage = WATCH_ORIGIN,
            stages = listOf(
                SleepStageSample(
                    start = today.minusDays(1).atTime(22, 0).toInstant(zone),
                    end = today.atTime(6, 0).toInstant(zone),
                    stage = SleepStageType.Light,
                ),
            ),
        )
        val duplicate = SleepSessionSample(
            start = today.minusDays(1).atTime(22, 30).toInstant(zone),
            end = today.atTime(6, 30).toInstant(zone),
            originPackage = WATCH_ORIGIN,
        )

        val nightly = fillNightlySleep(listOf(staged, duplicate), request)

        assertEquals(Duration.ofHours(8), nightly.last().duration)
        assertEquals(Duration.ofHours(8), nightly.last().zones?.light)
    }

    @Test
    @DisplayName("an unstaged overnight wins over a shorter staged leftover")
    fun longestBlockWinsOverShorterStagedLeftover() {
        val request = HealthMetricsRequest(zone = zone, now = now)
        val night = SleepSessionSample(
            start = today.minusDays(1).atTime(22, 0).toInstant(zone),
            end = today.atTime(6, 0).toInstant(zone),
            originPackage = WATCH_ORIGIN,
        )
        val leftover = SleepSessionSample(
            start = today.atTime(6, 20).toInstant(zone),
            end = today.atTime(7, 45).toInstant(zone),
            originPackage = FIT_ORIGIN,
            stages = listOf(
                SleepStageSample(
                    start = today.atTime(6, 20).toInstant(zone),
                    end = today.atTime(7, 45).toInstant(zone),
                    stage = SleepStageType.Light,
                ),
            ),
        )

        val nightly = fillNightlySleep(listOf(night, leftover), request)

        assertEquals(Duration.ofHours(8), nightly.last().duration)
        assertNull(nightly.last().zones)
    }

    @Test
    @DisplayName("same-origin split night keeps both fragments when a duplicate overlaps one half")
    fun sameOriginKeepsNonOverlappingFragmentsWhenDuplicatePresent() {
        val request = HealthMetricsRequest(zone = zone, now = now)
        val firstHalf = SleepSessionSample(
            start = today.minusDays(1).atTime(22, 0).toInstant(zone),
            end = today.atTime(2, 0).toInstant(zone),
            originPackage = WATCH_ORIGIN,
            stages = listOf(
                SleepStageSample(
                    start = today.minusDays(1).atTime(22, 0).toInstant(zone),
                    end = today.atTime(2, 0).toInstant(zone),
                    stage = SleepStageType.Light,
                ),
            ),
        )
        val secondHalf = SleepSessionSample(
            start = today.atTime(2, 20).toInstant(zone),
            end = today.atTime(6, 0).toInstant(zone),
            originPackage = WATCH_ORIGIN,
            stages = listOf(
                SleepStageSample(
                    start = today.atTime(2, 20).toInstant(zone),
                    end = today.atTime(6, 0).toInstant(zone),
                    stage = SleepStageType.Deep,
                ),
            ),
        )
        val duplicate = SleepSessionSample(
            start = today.atTime(2, 30).toInstant(zone),
            end = today.atTime(6, 30).toInstant(zone),
            originPackage = WATCH_ORIGIN,
        )

        val nightly = fillNightlySleep(listOf(firstHalf, secondHalf, duplicate), request)

        val todayNight = nightly.last()
        assertEquals(Duration.ofHours(8), todayNight.duration)
        assertEquals(Duration.ofHours(4), todayNight.zones?.light)
        assertEquals(Duration.ofHours(3).plusMinutes(40), todayNight.zones?.deep)
    }

    @Test
    @DisplayName("same-origin sessions more than 30 minutes apart do not merge")
    fun sameOriginGapOverThirtyMinutesDoesNotMerge() {
        val request = HealthMetricsRequest(zone = zone, now = now)
        val night = SleepSessionSample(
            start = today.minusDays(1).atTime(22, 0).toInstant(zone),
            end = today.atTime(6, 0).toInstant(zone),
            originPackage = WATCH_ORIGIN,
            stages = listOf(
                SleepStageSample(
                    start = today.minusDays(1).atTime(22, 0).toInstant(zone),
                    end = today.atTime(6, 0).toInstant(zone),
                    stage = SleepStageType.Light,
                ),
            ),
        )
        val leftover = SleepSessionSample(
            start = today.atTime(6, 31).toInstant(zone),
            end = today.atTime(7, 31).toInstant(zone),
            originPackage = WATCH_ORIGIN,
            stages = listOf(
                SleepStageSample(
                    start = today.atTime(6, 31).toInstant(zone),
                    end = today.atTime(7, 31).toInstant(zone),
                    stage = SleepStageType.Rem,
                ),
            ),
        )

        val nightly = fillNightlySleep(listOf(night, leftover), request)

        assertEquals(Duration.ofHours(8), nightly.last().duration)
        assertEquals(Duration.ofHours(8), nightly.last().zones?.light)
        assertEquals(Duration.ZERO, nightly.last().zones?.rem)
    }

    @Test
    @DisplayName("STR-264: awake time is not counted as sleep")
    fun awakeTimeIsNotCountedAsSleep() {
        val request = HealthMetricsRequest(zone = zone, now = now)
        // Sean's 2026-10-30/31 night as Google Health reports it: 8h in bed, of which
        // 14m awake; stages partition the session.
        val night = SleepSessionSample(
            start = today.minusDays(1).atTime(21, 56).toInstant(zone),
            end = today.atTime(5, 56).toInstant(zone),
            originPackage = WATCH_ORIGIN,
            stages = listOf(
                SleepStageSample(
                    start = today.minusDays(1).atTime(21, 56).toInstant(zone),
                    end = today.atTime(1, 54).toInstant(zone),
                    stage = SleepStageType.Light,
                ),
                SleepStageSample(
                    start = today.atTime(1, 54).toInstant(zone),
                    end = today.atTime(3, 25).toInstant(zone),
                    stage = SleepStageType.Deep,
                ),
                SleepStageSample(
                    start = today.atTime(3, 25).toInstant(zone),
                    end = today.atTime(5, 42).toInstant(zone),
                    stage = SleepStageType.Rem,
                ),
                SleepStageSample(
                    start = today.atTime(5, 42).toInstant(zone),
                    end = today.atTime(5, 56).toInstant(zone),
                    stage = SleepStageType.Awake,
                ),
            ),
        )

        val nightly = fillNightlySleep(listOf(night), request)

        val todayNight = nightly.last()
        assertEquals(Duration.ofHours(7).plusMinutes(46), todayNight.duration)
        assertEquals(Duration.ofHours(3).plusMinutes(58), todayNight.zones?.light)
        assertEquals(Duration.ofHours(1).plusMinutes(31), todayNight.zones?.deep)
        assertEquals(Duration.ofHours(2).plusMinutes(17), todayNight.zones?.rem)
        assertEquals(Duration.ofMinutes(14), todayNight.zones?.awake)
    }

    @Test
    @DisplayName("STR-264: out-of-bed time is not counted as sleep either")
    fun outOfBedTimeIsNotCountedAsSleep() {
        val request = HealthMetricsRequest(zone = zone, now = now)
        val night = SleepSessionSample(
            start = today.minusDays(1).atTime(22, 0).toInstant(zone),
            end = today.atTime(6, 0).toInstant(zone),
            originPackage = WATCH_ORIGIN,
            stages = listOf(
                SleepStageSample(
                    start = today.minusDays(1).atTime(22, 0).toInstant(zone),
                    end = today.atTime(4, 0).toInstant(zone),
                    stage = SleepStageType.Light,
                ),
                SleepStageSample(
                    start = today.atTime(4, 0).toInstant(zone),
                    end = today.atTime(4, 30).toInstant(zone),
                    stage = SleepStageType.Awake,
                ),
                SleepStageSample(
                    start = today.atTime(4, 30).toInstant(zone),
                    end = today.atTime(5, 0).toInstant(zone),
                    stage = SleepStageType.Awake,
                ),
                SleepStageSample(
                    start = today.atTime(5, 0).toInstant(zone),
                    end = today.atTime(6, 0).toInstant(zone),
                    stage = SleepStageType.Rem,
                ),
            ),
        )

        val nightly = fillNightlySleep(listOf(night), request)

        val todayNight = nightly.last()
        assertEquals(Duration.ofHours(7), todayNight.duration)
        // Both half-hours are awake (the mapper folds out-of-bed into awake), so both come out.
        assertEquals(Duration.ofHours(1), todayNight.zones?.awake)
    }

    @Test
    @DisplayName("STR-264: last night duration excludes awake time")
    fun lastNightDurationExcludesAwake() = runTest {
        prefs.setPreferences(HealthConnectPreferences(enabled = true))
        metrics.granted = setOf(HealthMetricType.Sleep)
        val night = SleepSessionSample(
            start = today.minusDays(1).atTime(22, 0).toInstant(zone),
            end = today.atTime(6, 0).toInstant(zone),
            stages = listOf(
                SleepStageSample(
                    start = today.minusDays(1).atTime(22, 0).toInstant(zone),
                    end = today.atTime(2, 0).toInstant(zone),
                    stage = SleepStageType.Light,
                ),
                SleepStageSample(
                    start = today.atTime(2, 0).toInstant(zone),
                    end = today.atTime(2, 30).toInstant(zone),
                    stage = SleepStageType.Awake,
                ),
                SleepStageSample(
                    start = today.atTime(2, 30).toInstant(zone),
                    end = today.atTime(6, 0).toInstant(zone),
                    stage = SleepStageType.Deep,
                ),
            ),
        )
        metrics.snapshot = HealthMetricsSnapshot(sleepSessions = listOf(night))

        val pages = load(zone, now)

        assertEquals(Duration.ofHours(7).plusMinutes(30), pages.recovery.lastNight?.duration)
        assertEquals(
            pages.recovery.nightlyDurations.last().duration,
            pages.recovery.lastNight?.duration,
        )
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
        assertEquals(7, pages.recovery.restingHeartRateDaily.size)
        assertTrue(pages.recovery.restingHeartRateDaily.all { it.value == null })
        assertEquals(7, pages.recovery.hrvDaily.size)
        assertTrue(pages.recovery.hrvDaily.all { it.value == null })
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

        val rhrDaily = pages.recovery.restingHeartRateDaily
        assertEquals(7, rhrDaily.size)
        assertEquals(50.0, rhrDaily[4].value)
        assertEquals(54.0, rhrDaily[6].value)
        assertNull(rhrDaily[0].value)
        val hrvDaily = pages.recovery.hrvDaily
        assertEquals(7, hrvDaily.size)
        assertEquals(40.0, hrvDaily[5].value)
        assertEquals(45.5, hrvDaily[6].value)
        assertNull(hrvDaily[0].value)
    }

    @Test
    @DisplayName("STR-112: rich lookback history yields p10-p90 personal-range bands")
    fun vitalRangesFromRichHistory() = runTest {
        prefs.setPreferences(HealthConnectPreferences(enabled = true))
        metrics.granted = setOf(HealthMetricType.RestingHeartRate, HealthMetricType.HeartRateVariability)
        val rhrLookback = (0..9).map { back ->
            HeartRateSample(today.minusDays(back.toLong()).atTime(7, 0).toInstant(zone), 50L + back)
        } + HeartRateSample(today.minusDays(60).atTime(7, 0).toInstant(zone), 200L)
        val hrvLookback = (0..9).map { back ->
            HrvSample(today.minusDays(back.toLong()).atTime(7, 0).toInstant(zone), 40.0 + back)
        } + HrvSample(today.minusDays(5).atTime(18, 0).toInstant(zone), 150.0)
        metrics.snapshot = HealthMetricsSnapshot(
            restingHeartRateLookback = rhrLookback,
            hrvLookback = hrvLookback,
        )

        val pages = load(zone, now)

        // RHR 50..59 over 10 days: p10 ≈ 50.9, p90 ≈ 58.1; the day-60 200 bpm is out of span.
        assertEquals(50.9, pages.recovery.restingHeartRateRange?.lower ?: Double.NaN, 1e-9)
        assertEquals(58.1, pages.recovery.restingHeartRateRange?.upper ?: Double.NaN, 1e-9)
        // HRV 40..49 plus a same-week 150 ms outlier: percentiles pin 41..49, not the max.
        assertEquals(VitalRange(lower = 41.0, upper = 49.0), pages.recovery.hrvRange)
    }

    @Test
    @DisplayName("STR-112: sparse lookback history falls back to no band")
    fun vitalRangesNullWhenHistorySparse() = runTest {
        prefs.setPreferences(HealthConnectPreferences(enabled = true))
        metrics.granted = setOf(HealthMetricType.RestingHeartRate, HealthMetricType.HeartRateVariability)
        metrics.snapshot = HealthMetricsSnapshot(
            // Ten samples but only three distinct days: multi-sample days don't count extra.
            restingHeartRateLookback = (0..9).map { index ->
                val day = today.minusDays((index % 3).toLong())
                HeartRateSample(day.atTime(7 + index, 0).toInstant(zone), 52L)
            },
            // Six distinct days is one short of the threshold.
            hrvLookback = (0..5).map { back ->
                HrvSample(today.minusDays(back.toLong()).atTime(7, 0).toInstant(zone), 45.0)
            },
        )

        val pages = load(zone, now)

        assertNull(pages.recovery.restingHeartRateRange)
        assertNull(pages.recovery.hrvRange)
    }

    @Test
    @DisplayName("STR-112: personal-range bands are null when vitals are not granted")
    fun vitalRangesNullWhenNotGranted() = runTest {
        prefs.setPreferences(HealthConnectPreferences(enabled = true))
        metrics.granted = setOf(HealthMetricType.Sleep)
        metrics.snapshot = HealthMetricsSnapshot(
            restingHeartRateLookback = (0..9).map { back ->
                HeartRateSample(today.minusDays(back.toLong()).atTime(7, 0).toInstant(zone), 52L)
            },
            hrvLookback = (0..9).map { back ->
                HrvSample(today.minusDays(back.toLong()).atTime(7, 0).toInstant(zone), 45.0)
            },
        )

        val pages = load(zone, now)

        assertNull(pages.recovery.restingHeartRateRange)
        assertNull(pages.recovery.hrvRange)
    }

    @Test
    @DisplayName("empty stages do not produce a zone bar")
    fun emptyStagesSkipZones() {
        val stages = emptyList<SleepStageSample>()
        assertNull(stages.toSleepZones())
    }

    @Test
    @DisplayName("STR-257: snapshot read failure carries a diagnostic and assembles empty pages")
    fun readFailureCarriesDiagnosticWithEmptyPages() = runTest {
        prefs.setPreferences(HealthConnectPreferences(enabled = true))
        val granted = setOf(HealthMetricType.Steps, HealthMetricType.Weight)
        metrics.granted = granted
        val cause = IllegalStateException("sdk blip")
        metrics.readSnapshotFailure = cause

        val pages = load(zone, now)

        assertTrue(pages.visible)
        assertEquals(1, pages.diagnostics.size)
        val diagnostic = pages.diagnostics.single()
        assertTrue(diagnostic is HealthConnectDiagnostic.SnapshotReadFailed)
        assertSame(cause, (diagnostic as HealthConnectDiagnostic.SnapshotReadFailed).cause)
        val expected = assembleHealthMetricsPages(
            granted = granted,
            snapshot = HealthMetricsSnapshot(grantedTypes = granted),
            request = HealthMetricsRequest(zone = zone, now = now),
        )
        assertEquals(expected, pages.copy(diagnostics = emptyList()))
    }

    @Test
    @DisplayName("STR-257: happy path carries no diagnostics")
    fun happyPathHasNoDiagnostics() = runTest {
        prefs.setPreferences(HealthConnectPreferences(enabled = true))
        metrics.granted = setOf(HealthMetricType.Steps)
        val todayStart = today.atStartOfDay().toInstant(zone)
        metrics.snapshot = HealthMetricsSnapshot(
            dailySteps = listOf(DailyStepCount(todayStart, 8_432L)),
        )

        val pages = load(zone, now)

        assertTrue(pages.visible)
        assertTrue(pages.diagnostics.isEmpty())
    }

    @Test
    @DisplayName("STR-257: cancellation is not converted to a diagnostic")
    fun cancellationPropagates() = runTest {
        prefs.setPreferences(HealthConnectPreferences(enabled = true))
        metrics.granted = setOf(HealthMetricType.Steps)
        metrics.readSnapshotFailure = CancellationException("cancelled")

        assertThrows<CancellationException> { load(zone, now) }
    }

    private fun kg(amount: Double): Weight = Weight(amount, Weight.WeightUnit.Kg)
}

private const val WATCH_ORIGIN = "com.google.android.wearable"
private const val FIT_ORIGIN = "com.google.android.apps.fitness"

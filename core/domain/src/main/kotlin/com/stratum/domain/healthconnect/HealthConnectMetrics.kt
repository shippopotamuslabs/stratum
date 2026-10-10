package com.stratum.domain.healthconnect

import com.stratum.domain.lift.Weight
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Health Connect record types the dashboard may read. */
enum class HealthMetricType {
    Steps,
    Weight,
    Sleep,
    RestingHeartRate,
    HeartRateVariability,
}

const val HEALTH_METRICS_DAY_COUNT = 7
const val LAST_NIGHT_LOOKBACK_HOURS = 36L
/** Trailing span for the RHR/HRV personal-range band (p10–p90 over this history). */
const val VITALS_LOOKBACK_DAYS = 30L
/** Distinct days with samples required before the band is shown (sparse-history fallback). */
const val VITALS_RANGE_MIN_HISTORY_DAYS = 7

/**
 * Query window for a dashboard metrics snapshot. [now] and [zone] are the only inputs; derived
 * bounds are local-calendar days (7 including today) plus a 36-hour last-night lookback.
 */
data class HealthMetricsRequest(
    val zone: ZoneId,
    val now: Instant,
) {
    val today: LocalDate = now.atZone(zone).toLocalDate()
    val windowStartDate: LocalDate = today.minusDays(HEALTH_METRICS_DAY_COUNT - 1L)
    val windowStart: Instant = windowStartDate.atStartOfDay(zone).toInstant()
    val todayStart: Instant = today.atStartOfDay(zone).toInstant()
    val windowEnd: Instant = today.plusDays(1).atStartOfDay(zone).toInstant()
    val lastNightLookbackStart: Instant = now.minus(Duration.ofHours(LAST_NIGHT_LOOKBACK_HOURS))
    /** Start of the trailing 30-day span backing the RHR/HRV personal-range band. */
    val vitalsLookbackStart: Instant = windowEnd.minus(Duration.ofDays(VITALS_LOOKBACK_DAYS))
    val localWindowStart: LocalDateTime = windowStartDate.atStartOfDay()
    val localWindowEnd: LocalDateTime = today.plusDays(1).atStartOfDay()
}

/** Raw Health Connect samples for one dashboard load. No Android types. */
data class HealthMetricsSnapshot(
    val grantedTypes: Set<HealthMetricType> = emptySet(),
    val dailySteps: List<DailyStepCount> = emptyList(),
    val weightSamples: List<WeightSample> = emptyList(),
    val sleepSessions: List<SleepSessionSample> = emptyList(),
    val restingHeartRateSamples: List<HeartRateSample> = emptyList(),
    val hrvSamples: List<HrvSample> = emptyList(),
    /**
     * Trailing-30-day vitals history backing the personal-range band, oldest first.
     * Includes the 7-day window samples; the reader bounds reads to
     * [HealthMetricsRequest.vitalsLookbackStart]..[HealthMetricsRequest.windowEnd].
     */
    val restingHeartRateLookback: List<HeartRateSample> = emptyList(),
    val hrvLookback: List<HrvSample> = emptyList(),
)

data class DailyStepCount(
    val dayStart: Instant,
    val count: Long,
)

data class WeightSample(
    val time: Instant,
    val weight: Weight,
)

data class SleepSessionSample(
    val start: Instant,
    val end: Instant,
    val stages: List<SleepStageSample> = emptyList(),
    val originPackage: String = "",
) {
    val duration: Duration = Duration.between(start, end)
}

data class SleepStageSample(
    val start: Instant,
    val end: Instant,
    val stage: SleepStageType,
)

enum class SleepStageType {
    Awake,
    Light,
    Deep,
    Rem,
    Sleeping,
    OutOfBed,
    Unknown,
}

data class HeartRateSample(
    val time: Instant,
    val beatsPerMinute: Long,
)

data class HrvSample(
    val time: Instant,
    val rmssdMillis: Double,
)

/** Assembled pager pages. [visible] is false when the dashboard should keep today's chrome. */
data class HealthMetricsPages(
    val visible: Boolean,
    val activity: ActivityMetrics = ActivityMetrics(),
    val weight: WeightMetrics = WeightMetrics(),
    val recovery: RecoveryMetrics = RecoveryMetrics(),
) {
    companion object {
        val Hidden: HealthMetricsPages = HealthMetricsPages(visible = false)
    }
}

data class ActivityMetrics(
    val todaySteps: Long? = null,
    val sevenDayAverage: Double? = null,
    val dailyCounts: List<DailyStepCount> = emptyList(),
)

data class WeightMetrics(
    val latest: WeightSample? = null,
    val sevenDayMean: Weight? = null,
    val samples: List<WeightSample> = emptyList(),
    /**
     * Trailing 3-day mean, one point per [samples] entry in the same order: each point's
     * weight is the mean of samples in the prior 3 days (time-aware, not "last N"). On
     * sparse weeks a point's window holds only itself, so the trend hugs the raw series.
     */
    val threeDayTrend: List<WeightSample> = emptyList(),
)

data class RecoveryMetrics(
    val lastNight: SleepNight? = null,
    val nightlyDurations: List<NightlySleepDuration> = emptyList(),
    val restingHeartRateBpm: Long? = null,
    val restingHeartRateSevenDayMean: Double? = null,
    val hrvRmssdMillis: Double? = null,
    /**
     * Per-day means across the 7-day window, one entry per [HealthMetricsRequest.windowDates]
     * (null where the day has no samples). Backs the STR-98 recovery trend viewport.
     */
    val restingHeartRateDaily: List<DailyVitalSample> = emptyList(),
    val hrvDaily: List<DailyVitalSample> = emptyList(),
    /**
     * Personal-range bands (trailing-30-day p10–p90). Null when the type is ungranted
     * or history covers fewer than [VITALS_RANGE_MIN_HISTORY_DAYS] distinct days.
     */
    val restingHeartRateRange: VitalRange? = null,
    val hrvRange: VitalRange? = null,
)

/** Inclusive personal-range band for one vital, in native units (bpm / ms). */
data class VitalRange(
    val lower: Double,
    val upper: Double,
)

/** One day's mean vital value; [value] is null when the day has no samples. */
data class DailyVitalSample(
    val dayStart: Instant,
    val value: Double?,
)

data class SleepNight(
    val start: Instant,
    val end: Instant,
    val duration: Duration,
    val zones: SleepZones? = null,
)

data class SleepZones(
    val light: Duration,
    val deep: Duration,
    val rem: Duration,
    val awake: Duration,
) {
    val total: Duration = light + deep + rem + awake

    operator fun plus(other: SleepZones): SleepZones =
        SleepZones(
            light = light + other.light,
            deep = deep + other.deep,
            rem = rem + other.rem,
            awake = awake + other.awake,
        )
}

data class NightlySleepDuration(
    val dayStart: Instant,
    val duration: Duration,
    val zones: SleepZones? = null,
)

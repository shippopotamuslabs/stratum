package com.stratum.domain.healthconnect

import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightConversion
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class LoadHealthConnectMetricsUseCase @Inject constructor(
    private val metricsPort: HealthConnectMetricsPort,
    private val preferencesPort: HealthConnectPreferencesPort,
) {
    suspend operator fun invoke(zone: ZoneId, now: Instant): HealthMetricsPages {
        val prefs = preferencesPort.getPreferences()
        if (!prefs.enabled) return HealthMetricsPages.Hidden
        if (!metricsPort.isAvailable()) return HealthMetricsPages.Hidden
        val granted = metricsPort.grantedReadTypes()
        if (granted.isEmpty()) return HealthMetricsPages.Hidden
        val request = HealthMetricsRequest(zone = zone, now = now)
        val snapshot = try {
            metricsPort.readSnapshot(request)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            HealthMetricsSnapshot(grantedTypes = granted)
        }
        return assembleHealthMetricsPages(granted, snapshot, request)
    }
}

internal fun assembleHealthMetricsPages(
    granted: Set<HealthMetricType>,
    snapshot: HealthMetricsSnapshot,
    request: HealthMetricsRequest,
): HealthMetricsPages {
    if (granted.isEmpty()) return HealthMetricsPages.Hidden
    return HealthMetricsPages(
        visible = true,
        activity = assembleActivity(granted, snapshot, request),
        weight = assembleWeight(granted, snapshot, request),
        recovery = assembleRecovery(granted, snapshot, request),
    )
}

private fun assembleActivity(
    granted: Set<HealthMetricType>,
    snapshot: HealthMetricsSnapshot,
    request: HealthMetricsRequest,
): ActivityMetrics {
    val filled = fillDailySteps(snapshot.dailySteps, request)
    if (HealthMetricType.Steps !in granted) {
        return ActivityMetrics(dailyCounts = filled)
    }
    val today = filled.lastOrNull()?.count
    val daysWithData = filled.filter { it.count > 0L }
    val average = if (daysWithData.isEmpty()) {
        null
    } else {
        daysWithData.sumOf { it.count }.toDouble() / daysWithData.size
    }
    return ActivityMetrics(
        todaySteps = today,
        sevenDayAverage = average,
        dailyCounts = filled,
    )
}

private fun assembleWeight(
    granted: Set<HealthMetricType>,
    snapshot: HealthMetricsSnapshot,
    request: HealthMetricsRequest,
): WeightMetrics {
    if (HealthMetricType.Weight !in granted) return WeightMetrics()
    val inWindow = snapshot.weightSamples
        .filter { !it.time.isBefore(request.windowStart) && it.time.isBefore(request.windowEnd) }
        .sortedBy { it.time }
    val latest = inWindow.maxByOrNull { it.time }
    val mean = if (inWindow.isEmpty()) {
        null
    } else {
        val kg = inWindow.map { WeightConversion.amountInKg(it.weight) }.average()
        Weight(kg, Weight.WeightUnit.Kg)
    }
    return WeightMetrics(latest = latest, sevenDayMean = mean, samples = inWindow)
}

private fun assembleRecovery(
    granted: Set<HealthMetricType>,
    snapshot: HealthMetricsSnapshot,
    request: HealthMetricsRequest,
): RecoveryMetrics {
    val lastNight = if (HealthMetricType.Sleep in granted) {
        snapshot.sleepSessions
            .filter { !it.end.isBefore(request.lastNightLookbackStart) && !it.end.isAfter(request.now) }
            .maxWithOrNull(compareBy<SleepSessionSample> { it.duration }.thenBy { it.end })
            ?.toSleepNight()
    } else {
        null
    }
    val nightly = fillNightlySleep(
        sessions = if (HealthMetricType.Sleep in granted) snapshot.sleepSessions else emptyList(),
        request = request,
    )
    val rhrSamples = if (HealthMetricType.RestingHeartRate in granted) {
        snapshot.restingHeartRateSamples.filter {
            !it.time.isBefore(request.windowStart) && it.time.isBefore(request.windowEnd)
        }
    } else {
        emptyList()
    }
    val latestRhr = rhrSamples.maxByOrNull { it.time }?.beatsPerMinute
    val rhrMean = if (rhrSamples.isEmpty()) {
        null
    } else {
        rhrSamples.map { it.beatsPerMinute.toDouble() }.average()
    }
    val latestHrv = if (HealthMetricType.HeartRateVariability in granted) {
        snapshot.hrvSamples
            .filter { !it.time.isBefore(request.windowStart) && it.time.isBefore(request.windowEnd) }
            .maxByOrNull { it.time }
            ?.rmssdMillis
    } else {
        null
    }
    return RecoveryMetrics(
        lastNight = lastNight,
        nightlyDurations = nightly,
        restingHeartRateBpm = latestRhr,
        restingHeartRateSevenDayMean = rhrMean,
        hrvRmssdMillis = latestHrv,
    )
}

internal fun fillDailySteps(
    sparse: List<DailyStepCount>,
    request: HealthMetricsRequest,
): List<DailyStepCount> {
    val byDay = sparse.associateBy { it.dayStart.atZone(request.zone).toLocalDate() }
    return request.windowDates().map { date ->
        val start = date.atStartOfDay(request.zone).toInstant()
        DailyStepCount(dayStart = start, count = byDay[date]?.count ?: 0L)
    }
}

internal fun fillNightlySleep(
    sessions: List<SleepSessionSample>,
    request: HealthMetricsRequest,
): List<NightlySleepDuration> {
    val byDay = sessions.groupBy { it.end.atZone(request.zone).toLocalDate() }
    return request.windowDates().map { date ->
        val start = date.atStartOfDay(request.zone).toInstant()
        val total = byDay[date].orEmpty().fold(Duration.ZERO) { acc, session -> acc + session.duration }
        NightlySleepDuration(dayStart = start, duration = total)
    }
}

internal fun SleepSessionSample.toSleepNight(): SleepNight =
    SleepNight(
        start = start,
        end = end,
        duration = duration,
        zones = stages.toSleepZones(),
    )

internal fun List<SleepStageSample>.toSleepZones(): SleepZones? {
    if (isEmpty()) return null
    var light = Duration.ZERO
    var deep = Duration.ZERO
    var rem = Duration.ZERO
    var awake = Duration.ZERO
    forEach { stage ->
        val length = Duration.between(stage.start, stage.end)
        when (stage.stage) {
            SleepStageType.Light -> light += length
            SleepStageType.Deep -> deep += length
            SleepStageType.Rem -> rem += length
            SleepStageType.Awake, SleepStageType.OutOfBed -> awake += length
            SleepStageType.Sleeping, SleepStageType.Unknown -> Unit
        }
    }
    val zones = SleepZones(light = light, deep = deep, rem = rem, awake = awake)
    return zones.takeIf { !it.total.isZero }
}

private fun HealthMetricsRequest.windowDates(): List<LocalDate> =
    (0 until HEALTH_METRICS_DAY_COUNT).map { windowStartDate.plusDays(it.toLong()) }

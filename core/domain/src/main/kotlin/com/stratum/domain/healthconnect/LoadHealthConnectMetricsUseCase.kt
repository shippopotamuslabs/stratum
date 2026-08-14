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
    val sleepSessions =
        if (HealthMetricType.Sleep in granted) snapshot.sleepSessions else emptyList()
    val nightly = fillNightlySleep(sessions = sleepSessions, request = request)
    val lastNight = lastNightFromNightly(nightly, sleepSessions, request)
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
        val (total, zones) = summarizeSleepSessions(byDay[date].orEmpty())
        NightlySleepDuration(dayStart = start, duration = total, zones = zones)
    }
}

/**
 * Health Connect often stores the same night from more than one app. Overlapping sessions are
 * collapsed to one (prefer staged, then longest); non-overlapping naps still add.
 */
internal fun summarizeSleepSessions(
    sessions: List<SleepSessionSample>,
): Pair<Duration, SleepZones?> {
    if (sessions.isEmpty()) return Duration.ZERO to null
    var duration = Duration.ZERO
    var zones: SleepZones? = null
    for (cluster in clusterOverlappingSleepSessions(sessions)) {
        val chosen = preferredSleepSession(cluster) ?: continue
        duration += chosen.duration
        val chosenZones = chosen.stages.toSleepZones()
        zones = when {
            chosenZones == null -> zones
            zones == null -> chosenZones
            else -> zones + chosenZones
        }
    }
    return duration to zones
}

internal fun clusterOverlappingSleepSessions(
    sessions: List<SleepSessionSample>,
): List<List<SleepSessionSample>> {
    if (sessions.isEmpty()) return emptyList()
    val remaining = sessions.toMutableList()
    val clusters = mutableListOf<List<SleepSessionSample>>()
    while (remaining.isNotEmpty()) {
        val cluster = mutableListOf(remaining.removeAt(remaining.lastIndex))
        var index = 0
        while (index < remaining.size) {
            if (cluster.any { it.overlaps(remaining[index]) }) {
                cluster += remaining.removeAt(index)
                index = 0
            } else {
                index++
            }
        }
        clusters += cluster
    }
    return clusters
}

internal fun preferredSleepSession(cluster: List<SleepSessionSample>): SleepSessionSample? =
    cluster.maxWithOrNull(
        compareBy<SleepSessionSample> { session -> session.stages.toSleepZones() != null }
            .thenBy { it.duration }
            .thenBy { it.end },
    )

internal fun SleepSessionSample.overlaps(other: SleepSessionSample): Boolean =
    start.isBefore(other.end) && other.start.isBefore(end)

/**
 * Last night is the most recent local day that has a session ending in the 36-hour lookback —
 * the same bucket the 7-day chart uses for that weekday. Picking the longest session instead
 * would show Thursday when Friday already has data.
 */
internal fun lastNightFromNightly(
    nightly: List<NightlySleepDuration>,
    sessions: List<SleepSessionSample>,
    request: HealthMetricsRequest,
): SleepNight? {
    val lastDate = sessions
        .asSequence()
        .filter { !it.end.isBefore(request.lastNightLookbackStart) && !it.end.isAfter(request.now) }
        .maxOfOrNull { it.end.atZone(request.zone).toLocalDate() }
        ?: return null
    val night = nightly.firstOrNull { bucket ->
        bucket.dayStart.atZone(request.zone).toLocalDate() == lastDate
    } ?: return null
    if (night.duration.isZero) return null
    val daySessions = sessions.filter { session ->
        session.end.atZone(request.zone).toLocalDate() == lastDate
    }
    if (daySessions.isEmpty()) return null
    return SleepNight(
        start = daySessions.minOf { it.start },
        end = daySessions.maxOf { it.end },
        duration = night.duration,
        zones = night.zones,
    )
}

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

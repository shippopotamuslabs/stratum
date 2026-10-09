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
    return WeightMetrics(
        latest = latest,
        sevenDayMean = mean,
        samples = inWindow,
        threeDayTrend = trailingWeightMeans(inWindow),
    )
}

/**
 * Trailing 3-day mean over time-sorted in-window samples, one point per sample in the same
 * order. Each point averages the samples in `[time − 3 days, time]` — a time-aware window,
 * so sparse weeks hug the raw series instead of smearing across gaps.
 */
internal fun trailingWeightMeans(sorted: List<WeightSample>): List<WeightSample> =
    sorted.map { point ->
        val floor = point.time.minus(TRAILING_MEAN_WINDOW)
        val kg = sorted
            .filter { !it.time.isBefore(floor) && !it.time.isAfter(point.time) }
            .map { WeightConversion.amountInKg(it.weight) }
            .average()
        WeightSample(time = point.time, weight = Weight(kg, Weight.WeightUnit.Kg))
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
    val hrvInWindow = if (HealthMetricType.HeartRateVariability in granted) {
        snapshot.hrvSamples.filter {
            !it.time.isBefore(request.windowStart) && it.time.isBefore(request.windowEnd)
        }
    } else {
        emptyList()
    }
    return RecoveryMetrics(
        lastNight = lastNight,
        nightlyDurations = nightly,
        restingHeartRateBpm = latestRhr,
        restingHeartRateSevenDayMean = rhrMean,
        hrvRmssdMillis = latestHrv,
        restingHeartRateDaily = dailyVitalMeans(
            samples = rhrSamples.map { it.time to it.beatsPerMinute.toDouble() },
            request = request,
        ),
        hrvDaily = dailyVitalMeans(
            samples = hrvInWindow.map { it.time to it.rmssdMillis },
            request = request,
        ),
    )
}

/**
 * Per-day mean of a vital's samples across the 7-day window, in window order.
 * Days without samples yield null — the UI layer drops those points (a 0 bpm /
 * 0 ms reading is never legitimate data).
 */
private fun dailyVitalMeans(
    samples: List<Pair<Instant, Double>>,
    request: HealthMetricsRequest,
): List<DailyVitalSample> {
    val byDay = samples.groupBy({ it.first.atZone(request.zone).toLocalDate() }, { it.second })
    return request.windowDays().map { (date, start) ->
        DailyVitalSample(dayStart = start, value = byDay[date]?.average())
    }
}

internal fun fillDailySteps(
    sparse: List<DailyStepCount>,
    request: HealthMetricsRequest,
): List<DailyStepCount> {
    val byDay = sparse.associateBy { it.dayStart.atZone(request.zone).toLocalDate() }
    return request.windowDays().map { (date, start) ->
        DailyStepCount(dayStart = start, count = byDay[date]?.count ?: 0L)
    }
}

internal fun fillNightlySleep(
    sessions: List<SleepSessionSample>,
    request: HealthMetricsRequest,
): List<NightlySleepDuration> {
    val byDay = sessions.groupBy { it.end.atZone(request.zone).toLocalDate() }
    return request.windowDays().map { (date, start) ->
        val summary = summarizeSleepSessions(byDay[date].orEmpty())
        NightlySleepDuration(dayStart = start, duration = summary.duration, zones = summary.zones)
    }
}

/**
 * One overnight block per wake day. Sessions are grouped by [SleepSessionSample.originPackage]
 * so a watch night is not glued to a phone leftover. Within a known origin, overlapping or
 * nearly contiguous records merge; blank origins only collapse true overlaps. Across origins,
 * overlapping duplicates prefer staged; leftover blocks do not add.
 */
internal data class SleepDaySummary(
    val duration: Duration,
    val zones: SleepZones?,
    val start: Instant? = null,
    val end: Instant? = null,
)

internal fun summarizeSleepSessions(sessions: List<SleepSessionSample>): SleepDaySummary {
    if (sessions.isEmpty()) return SleepDaySummary(duration = Duration.ZERO, zones = null)
    val originBlocks = clusterSleepSessionsByOrigin(sessions).map(::summarizeSleepCluster)
    val resolvedDuplicates = clusterConnected(originBlocks) { left, right ->
        left.overlaps(right)
    }.map(::preferredSleepSummary)
    return resolvedDuplicates.maxByOrNull { it.duration }
        ?: SleepDaySummary(duration = Duration.ZERO, zones = null)
}

internal fun clusterSleepSessionsByOrigin(
    sessions: List<SleepSessionSample>,
): List<List<SleepSessionSample>> =
    sessions.groupBy { it.originPackage }.values.flatMap { originSessions ->
        val knownOrigin = originSessions.first().originPackage.isNotBlank()
        clusterConnected(originSessions) { left, right ->
            left.overlaps(right) || (knownOrigin && left.contiguousWith(right))
        }
    }

internal fun summarizeSleepCluster(cluster: List<SleepSessionSample>): SleepDaySummary {
    val selected = selectNonOverlappingSessions(cluster)
    if (selected.isEmpty()) return SleepDaySummary(duration = Duration.ZERO, zones = null)
    val start = selected.minOf { it.start }
    val end = selected.maxOf { it.end }
    val zones = selected.mapNotNull { it.stages.toSleepZones() }.reduceOrNull { acc, next -> acc + next }
    return SleepDaySummary(
        duration = Duration.between(start, end),
        zones = zones,
        start = start,
        end = end,
    )
}

internal fun selectNonOverlappingSessions(
    cluster: List<SleepSessionSample>,
): List<SleepSessionSample> {
    val ranked = cluster.sortedWith(
        compareByDescending<SleepSessionSample> { it.stages.toSleepZones() != null }
            .thenByDescending { it.duration }
            .thenByDescending { it.end },
    )
    val selected = mutableListOf<SleepSessionSample>()
    for (session in ranked) {
        if (selected.none { it.overlaps(session) }) {
            selected += session
        }
    }
    return selected
}

internal fun preferredSleepSummary(group: List<SleepDaySummary>): SleepDaySummary =
    group.maxWithOrNull(
        compareBy<SleepDaySummary> { it.zones != null }.thenBy { it.duration },
    ) ?: SleepDaySummary(duration = Duration.ZERO, zones = null)

internal fun <T> clusterConnected(
    items: List<T>,
    connected: (T, T) -> Boolean,
): List<List<T>> {
    if (items.isEmpty()) return emptyList()
    val remaining = items.toMutableList()
    val clusters = mutableListOf<List<T>>()
    while (remaining.isNotEmpty()) {
        val cluster = mutableListOf(remaining.removeAt(remaining.lastIndex))
        var index = 0
        while (index < remaining.size) {
            if (cluster.any { connected(it, remaining[index]) }) {
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

internal fun SleepDaySummary.overlaps(other: SleepDaySummary): Boolean {
    val thisStart = start ?: return false
    val thisEnd = end ?: return false
    val otherStart = other.start ?: return false
    val otherEnd = other.end ?: return false
    return thisStart.isBefore(otherEnd) && otherStart.isBefore(thisEnd)
}

internal fun SleepSessionSample.overlaps(other: SleepSessionSample): Boolean =
    start.isBefore(other.end) && other.start.isBefore(end)

internal fun SleepSessionSample.contiguousWith(other: SleepSessionSample): Boolean {
    val gap = when {
        !end.isAfter(other.start) -> Duration.between(end, other.start)
        !other.end.isAfter(start) -> Duration.between(other.end, start)
        else -> Duration.ZERO
    }
    return gap <= SAME_ORIGIN_CONTIGUOUS_GAP
}

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
    val chartHasSleep = nightly.any { bucket ->
        bucket.dayStart.atZone(request.zone).toLocalDate() == lastDate && !bucket.duration.isZero
    }
    val summary = summarizeSleepSessions(
        sessions.filter { session ->
            session.end.atZone(request.zone).toLocalDate() == lastDate
        },
    )
    val start = summary.start
    val end = summary.end
    if (!chartHasSleep || start == null || end == null) return null
    return SleepNight(
        start = start,
        end = end,
        duration = summary.duration,
        zones = summary.zones,
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

/**
 * Local dates plus their day-start instants across the 7-day window, in window order.
 * The single window-expansion behind the step, sleep, and vital fills.
 */
private fun HealthMetricsRequest.windowDays(): List<Pair<LocalDate, Instant>> =
    windowDates().map { date -> date to date.atStartOfDay(zone).toInstant() }

private val SAME_ORIGIN_CONTIGUOUS_GAP: Duration = Duration.ofMinutes(30)
private val TRAILING_MEAN_WINDOW: Duration = Duration.ofDays(3)

package com.stratum

import com.stratum.core.ui.component.TrendPointUi
import com.stratum.domain.healthconnect.ActivityMetrics
import com.stratum.domain.healthconnect.DailyVitalSample
import com.stratum.domain.healthconnect.HEALTH_METRICS_DAY_COUNT
import com.stratum.domain.healthconnect.HealthMetricsPages
import com.stratum.domain.healthconnect.NightlySleepDuration
import com.stratum.domain.healthconnect.RecoveryMetrics
import com.stratum.domain.healthconnect.SleepZones
import com.stratum.domain.healthconnect.WeightMetrics
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightConversion
import com.stratum.domain.lift.shortLabel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

const val HEALTH_PAGER_PAGE_COUNT = 3

data class DashboardHealthUiState(
    val visible: Boolean = false,
    val selectedPage: Int = 0,
    val activity: ActivityHealthPageUi = ActivityHealthPageUi(),
    val weight: WeightHealthPageUi = WeightHealthPageUi(),
    val recovery: RecoveryHealthPageUi = RecoveryHealthPageUi(),
)

data class ActivityHealthPageUi(
    val todayStepsText: String? = null,
    val sevenDayAverageText: String? = null,
    val dailyPoints: List<TrendPointUi> = emptyList(),
    val dayLabels: List<String> = emptyList(),
)

data class WeightHealthPageUi(
    val latestText: String? = null,
    val latestDateText: String? = null,
    val sevenDayMeanText: String? = null,
    val deltaText: String? = null,
    val trendPoints: List<TrendPointUi> = emptyList(),
    /** Muted trailing-mean series; 1:1 with [trendPoints] (`weekIndex`-aligned). Empty when unknown. */
    val trendMeanPoints: List<TrendPointUi> = emptyList(),
    val dayLabels: List<String> = emptyList(),
)

data class RecoveryHealthPageUi(
    val lastNightDurationText: String? = null,
    val nightlyAverageText: String? = null,
    val restingHrText: String? = null,
    val restingHrAverageText: String? = null,
    val hrvText: String? = null,
    val hrvAverageText: String? = null,
    val nightlyPoints: List<TrendPointUi> = emptyList(),
    val lightNightlyPoints: List<TrendPointUi> = emptyList(),
    val deepNightlyPoints: List<TrendPointUi> = emptyList(),
    val remNightlyPoints: List<TrendPointUi> = emptyList(),
    val awakeNightlyPoints: List<TrendPointUi> = emptyList(),
    /** Sparse per-day vitals; only days with samples are present (null = no data, dropped). */
    val restingHrDailyPoints: List<TrendPointUi> = emptyList(),
    val hrvDailyPoints: List<TrendPointUi> = emptyList(),
    val dayLabels: List<String> = emptyList(),
)

enum class SleepStageUi {
    Light,
    Deep,
    Rem,
    Awake,
}

internal fun ActivityHealthPageUi.hasChart(): Boolean = dailyPoints.any { it.value > 0f }

internal fun WeightHealthPageUi.hasChart(): Boolean = trendPoints.size >= 2

internal fun RecoveryHealthPageUi.hasSleepChart(): Boolean =
    nightlyPoints.any { it.value > 0f } ||
        lightNightlyPoints.any { it.value > 0f } ||
        deepNightlyPoints.any { it.value > 0f } ||
        remNightlyPoints.any { it.value > 0f } ||
        awakeNightlyPoints.any { it.value > 0f }

internal fun RecoveryHealthPageUi.hasChart(): Boolean =
    hasSleepChart() ||
        restingHrDailyPoints.isNotEmpty() ||
        hrvDailyPoints.isNotEmpty()

/**
 * STR-96: the "no sleep" empty copy must only show when the window truly has no
 * sleep data — never above a non-empty chart. [lastNightDurationText] is null
 * whenever the most recent night is missing, but older nights in the window can
 * still carry durations (non-zero bars below the copy). Scoped to sleep data only:
 * vitals may legitimately render while the sleep tab is empty.
 */
internal fun RecoveryHealthPageUi.showSleepEmptyText(): Boolean =
    lastNightDurationText == null && !hasSleepChart()

internal fun DashboardHealthUiState.hasChart(): Boolean =
    activity.hasChart() || weight.hasChart() || recovery.hasChart()

internal fun HealthMetricsPages.toHealthUi(
    displayUnit: Weight.WeightUnit,
    zone: ZoneId,
): DashboardHealthUiState {
    if (!visible) return DashboardHealthUiState()
    return DashboardHealthUiState(
        visible = true,
        activity = activity.toUi(zone),
        weight = weight.toUi(displayUnit, zone),
        recovery = recovery.toUi(zone),
    )
}

private fun ActivityMetrics.toUi(zone: ZoneId): ActivityHealthPageUi {
    val points = dailyCounts.mapIndexed { index, day ->
        TrendPointUi(weekIndex = index, value = day.count.toFloat())
    }
    val labels = dailyCounts.map { shortWeekdayLabel(it.dayStart, zone) }
    return ActivityHealthPageUi(
        todayStepsText = todaySteps
            ?.takeIf { dailyCounts.any { day -> day.count > 0L } }
            ?.let { formatStepCount(it) },
        sevenDayAverageText = sevenDayAverage?.let { formatStepCount(it.toLong()) },
        dailyPoints = points.ifEmpty { zeroWeekPoints() },
        dayLabels = labels.ifEmpty { emptyList() },
    )
}

private fun WeightMetrics.toUi(
    displayUnit: Weight.WeightUnit,
    zone: ZoneId,
): WeightHealthPageUi {
    val latest = latest
    val mean = sevenDayMean
    val latestAmount = latest?.let { convertBodyWeight(it.weight, displayUnit) }
    val meanAmount = mean?.let { convertBodyWeight(it, displayUnit) }
    val delta = if (latestAmount != null && meanAmount != null) latestAmount - meanAmount else null
    val points = samples.mapIndexed { index, sample ->
        TrendPointUi(
            weekIndex = index,
            value = convertBodyWeight(sample.weight, displayUnit).toFloat(),
        )
    }
    // Domain emits one trend point per sample in the same order, so the index aligns both series.
    val meanPoints = threeDayTrend.mapIndexed { index, sample ->
        TrendPointUi(
            weekIndex = index,
            value = convertBodyWeight(sample.weight, displayUnit).toFloat(),
        )
    }
    return WeightHealthPageUi(
        latestText = latestAmount?.let { formatBodyWeight(it, displayUnit) },
        latestDateText = latest?.time?.let { formatShortDate(it, zone) },
        sevenDayMeanText = meanAmount?.let { formatBodyWeight(it, displayUnit) },
        deltaText = delta?.let { formatWeightDelta(it, displayUnit) },
        trendPoints = points,
        trendMeanPoints = meanPoints,
        dayLabels = samples.map { shortWeekdayLabel(it.time, zone) },
    )
}

private fun RecoveryMetrics.toUi(zone: ZoneId): RecoveryHealthPageUi {
    val points = nightlyDurations.mapIndexed { index, night ->
        TrendPointUi(
            weekIndex = index,
            value = night.duration.toHoursFloat(),
        )
    }
    val labels = nightlyDurations.map { shortWeekdayLabel(it.dayStart, zone) }
    val nightlyAverage = nightlyDurations
        .map { it.duration }
        .filter { !it.isZero }
        .takeIf { it.isNotEmpty() }
        ?.let { durations ->
            val avgMinutes = durations.sumOf { it.toMinutes() } / durations.size
            formatHoursMinutes(Duration.ofMinutes(avgMinutes))
        }
    return RecoveryHealthPageUi(
        lastNightDurationText = lastNight?.duration?.let { formatHoursMinutes(it) },
        nightlyAverageText = nightlyAverage,
        restingHrText = restingHeartRateBpm?.let { "$it bpm" },
        restingHrAverageText = restingHeartRateSevenDayMean?.let {
            "${it.toLong()} bpm"
        },
        hrvText = hrvRmssdMillis?.let { String.format(Locale.US, "%.0f ms", it) },
        hrvAverageText = hrvDaily.mapNotNull { it.value }
            .takeIf { it.isNotEmpty() }
            ?.let { String.format(Locale.US, "%.0f ms", it.average()) },
        nightlyPoints = points.ifEmpty { zeroWeekPoints() },
        lightNightlyPoints = nightlyDurations.toStagePoints { it.light },
        deepNightlyPoints = nightlyDurations.toStagePoints { it.deep },
        remNightlyPoints = nightlyDurations.toStagePoints { it.rem },
        awakeNightlyPoints = nightlyDurations.toStagePoints { it.awake },
        restingHrDailyPoints = restingHeartRateDaily.toVitalPoints(),
        hrvDailyPoints = hrvDaily.toVitalPoints(),
        dayLabels = labels.ifEmpty { emptyList() },
    )
}

/**
 * Sparse trend points: days with a null mean (no samples that day) are dropped.
 * [weekIndex] keeps the surviving points aligned with the 7-day labels.
 */
private fun List<DailyVitalSample>.toVitalPoints(): List<TrendPointUi> =
    mapIndexedNotNull { index, sample ->
        sample.value?.let { TrendPointUi(weekIndex = index, value = it.toFloat()) }
    }

private fun List<NightlySleepDuration>.toStagePoints(
    hours: (SleepZones) -> Duration,
): List<TrendPointUi> {
    if (isEmpty()) return zeroWeekPoints()
    return mapIndexed { index, night ->
        TrendPointUi(
            weekIndex = index,
            value = night.zones?.let(hours).toHoursFloat(),
        )
    }
}

private fun Duration?.toHoursFloat(): Float =
    this?.toMinutes()?.toFloat()?.div(MINUTES_PER_HOUR) ?: 0f

internal fun formatStepCount(count: Long): String =
    String.format(Locale.US, "%,d", count)

internal fun formatHoursMinutes(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.minusHours(hours).toMinutes()
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}

internal fun formatBodyWeight(amount: Double, unit: Weight.WeightUnit): String =
    String.format(Locale.US, "%.1f %s", amount, unit.shortLabel())

internal fun formatWeightDelta(delta: Double, unit: Weight.WeightUnit): String {
    val sign = if (delta >= 0) "+" else "−"
    return sign + String.format(Locale.US, "%.1f %s", abs(delta), unit.shortLabel())
}

internal fun formatShortDate(time: Instant, zone: ZoneId): String =
    SHORT_DATE.format(time.atZone(zone))

private fun convertBodyWeight(weight: Weight, displayUnit: Weight.WeightUnit): Double =
    WeightConversion.convertAmount(weight.amount, weight.unit, displayUnit)

private fun zeroWeekPoints(): List<TrendPointUi> =
    (0 until HEALTH_METRICS_DAY_COUNT).map { TrendPointUi(weekIndex = it, value = 0f) }

private const val MINUTES_PER_HOUR = 60f

private val SHORT_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d", Locale.US)

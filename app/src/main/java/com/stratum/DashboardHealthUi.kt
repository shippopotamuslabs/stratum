package com.stratum

import com.stratum.core.ui.component.TrendPointUi
import com.stratum.domain.healthconnect.ActivityMetrics
import com.stratum.domain.healthconnect.HEALTH_METRICS_DAY_COUNT
import com.stratum.domain.healthconnect.HealthMetricsPages
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
)

data class RecoveryHealthPageUi(
    val lastNightDurationText: String? = null,
    val zones: SleepZonesUi? = null,
    val restingHrText: String? = null,
    val restingHrAverageText: String? = null,
    val hrvText: String? = null,
    val nightlyPoints: List<TrendPointUi> = emptyList(),
    val dayLabels: List<String> = emptyList(),
)

data class SleepZonesUi(
    val lightFraction: Float,
    val deepFraction: Float,
    val remFraction: Float,
    val awakeFraction: Float,
)

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
        todayStepsText = todaySteps?.let { formatStepCount(it) },
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
    return WeightHealthPageUi(
        latestText = latestAmount?.let { formatBodyWeight(it, displayUnit) },
        latestDateText = latest?.time?.let { formatShortDate(it, zone) },
        sevenDayMeanText = meanAmount?.let { formatBodyWeight(it, displayUnit) },
        deltaText = delta?.let { formatWeightDelta(it, displayUnit) },
        trendPoints = points,
    )
}

private fun RecoveryMetrics.toUi(zone: ZoneId): RecoveryHealthPageUi {
    val points = nightlyDurations.mapIndexed { index, night ->
        TrendPointUi(
            weekIndex = index,
            value = night.duration.toMinutes().toFloat() / 60f,
        )
    }
    val labels = nightlyDurations.map { shortWeekdayLabel(it.dayStart, zone) }
    return RecoveryHealthPageUi(
        lastNightDurationText = lastNight?.duration?.let { formatHoursMinutes(it) },
        zones = lastNight?.zones?.toUi(),
        restingHrText = restingHeartRateBpm?.let { "$it bpm" },
        restingHrAverageText = restingHeartRateSevenDayMean?.let {
            "${it.toLong()} bpm"
        },
        hrvText = hrvRmssdMillis?.let { String.format(Locale.US, "%.0f ms", it) },
        nightlyPoints = points.ifEmpty { zeroWeekPoints() },
        dayLabels = labels.ifEmpty { emptyList() },
    )
}

private fun SleepZones.toUi(): SleepZonesUi? {
    val totalMs = total.toMillis().toFloat()
    if (totalMs <= 0f) return null
    return SleepZonesUi(
        lightFraction = light.toMillis() / totalMs,
        deepFraction = deep.toMillis() / totalMs,
        remFraction = rem.toMillis() / totalMs,
        awakeFraction = awake.toMillis() / totalMs,
    )
}

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

private val SHORT_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d", Locale.US)

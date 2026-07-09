package com.stratum.core.ui.component

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Shared number formatting for detail chart axes and stats. Single source of truth so Avg/Best
 * labels never drift from Y-axis ticks.
 */
fun formatChartNumber(value: Double, scale: DetailYScale): String {
    if (abs(value) < ZERO_EPSILON) return "0"
    return when (scale) {
        DetailYScale.Weight -> {
            if (abs(value) >= WEIGHT_INTEGER_THRESHOLD) {
                value.toInt().toString()
            } else {
                trimTrailingZeros(String.format(Locale.US, "%.1f", value))
            }
        }
        DetailYScale.Volume -> {
            if (abs(value - value.toLong()) < ZERO_EPSILON) {
                value.toLong().toString()
            } else {
                trimTrailingZeros(String.format(Locale.US, "%.1f", value))
            }
        }
        DetailYScale.Progress -> {
            val magnitude = abs(value)
            val raw = when {
                magnitude >= PROGRESS_THRESHOLD_ONES -> String.format(Locale.US, "%.2f", value)
                magnitude >= PROGRESS_THRESHOLD_TENTHS -> String.format(Locale.US, "%.2f", value)
                magnitude >= PROGRESS_THRESHOLD_HUNDREDTHS -> String.format(Locale.US, "%.3f", value)
                magnitude >= PROGRESS_THRESHOLD_THOUSANDTHS -> String.format(Locale.US, "%.4f", value)
                else -> String.format(Locale.US, "%.2e", value)
            }
            if (raw.contains('e', ignoreCase = true)) raw else trimTrailingZeros(raw)
        }
    }
}

/**
 * Formats a week-start [Instant] (stored as Monday 00:00 UTC) for display without local-timezone
 * day-shift (e.g. Sunday evening in US zones).
 */
fun formatWeekStartLabel(weekStart: Instant, pattern: String = "MMM d"): String {
    val formatter = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
    return formatter.format(weekStart.atZone(ZoneOffset.UTC))
}

fun formatWeekStartRangeLabel(from: Instant?, to: Instant?): String {
    if (from == null || to == null) return ""
    val fromText = formatWeekStartLabel(from, "MMM d, yyyy")
    val toText = formatWeekStartLabel(to, "MMM d, yyyy")
    return if (fromText == toText) fromText else "$fromText – $toText"
}

/** UTC epoch day for a week-start instant (matches [formatWeekStartLabel]). */
fun weekStartToEpochDay(weekStartEpochMillis: Long): Long =
    Instant.ofEpochMilli(weekStartEpochMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .toEpochDay()

fun List<Pair<Long, Float>>.toDetailChartPoints(): List<DetailChartPoint> =
    map { (millis, value) ->
        DetailChartPoint(xEpochDay = weekStartToEpochDay(millis), value = value)
    }

private fun trimTrailingZeros(formatted: String): String =
    formatted.trimEnd('0').trimEnd('.')

private const val ZERO_EPSILON = 1e-12
private const val WEIGHT_INTEGER_THRESHOLD = 100.0
private const val PROGRESS_THRESHOLD_ONES = 1.0
private const val PROGRESS_THRESHOLD_TENTHS = 0.1
private const val PROGRESS_THRESHOLD_HUNDREDTHS = 0.01
private const val PROGRESS_THRESHOLD_THOUSANDTHS = 0.001

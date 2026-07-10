package com.stratum.core.ui.component

import java.util.Locale

/** Shared presentation formatters for workout recap / history surfaces. */
const val RIR_NONE_LABEL = "—"

fun formatWorkoutDuration(millis: Long): String {
    val totalSec = millis / MILLIS_PER_SEC
    val hrs = totalSec / SECS_PER_HOUR
    val mins = (totalSec % SECS_PER_HOUR) / SECS_PER_MIN
    val secs = totalSec % SECS_PER_MIN
    return if (hrs > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%dm %02ds", mins, secs)
    }
}

fun formatSetPerformance(weightAmount: Double, reps: Int): String =
    if (weightAmount == 0.0) {
        reps.toString()
    } else {
        "${formatWeightAmount(weightAmount)} × $reps"
    }

fun formatWeightAmount(weight: Double): String {
    val rounded = (weight * TEN_FACTOR).toInt() / TEN_FACTOR.toDouble()
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

private const val MILLIS_PER_SEC = 1000
private const val SECS_PER_HOUR = 3600
private const val SECS_PER_MIN = 60
private const val TEN_FACTOR = 10

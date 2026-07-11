package com.stratum.core.ui.component

import java.util.Locale
import kotlin.math.roundToInt

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

/**
 * Formats a weight amount for display: blank when 0 (cell placeholder contract), otherwise rounded
 * to at most one decimal with trailing `.0` stripped.
 *
 * When [assistMode] is true the amount is sign-stripped first (assisted loads are stored negative;
 * the user sees a positive assistance figure).
 */
fun formatWeightAmount(weight: Double, assistMode: Boolean = false): String {
    val display = if (assistMode) kotlin.math.abs(weight) else weight
    if (display == 0.0) return ""
    val rounded = (display * TEN_FACTOR).roundToInt() / TEN_FACTOR.toDouble()
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

/**
 * Formats a tier bound / increment amount for equipment and lift increment editors and
 * proposal summaries. Unlike [formatWeightAmount], zero is rendered as `"0"` (band lower
 * bounds are often zero).
 */
fun formatIncrementAmount(amount: Double): String {
    val rounded = (amount * TEN_FACTOR).roundToInt() / TEN_FACTOR.toDouble()
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

/**
 * Compact multi-tier summary for proposal cards, e.g. `"0–70 → 2.5 · 70+ → 5"`.
 *
 * [tiers] is a list of (lower, upper?, increment) amounts — presentation layer maps domain
 * [com.stratum.domain.lift.IncrementTier] into this shape.
 */
fun formatIncrementTierSummary(
    tiers: List<Triple<Double, Double?, Double>>,
): String =
    tiers.joinToString(" · ") { (lower, upper, increment) ->
        val lowerLabel = formatIncrementAmount(lower)
        val incLabel = formatIncrementAmount(increment)
        val upperBound = upper
        if (upperBound != null) {
            "$lowerLabel–${formatIncrementAmount(upperBound)} → $incLabel"
        } else {
            "$lowerLabel+ → $incLabel"
        }
    }

private const val MILLIS_PER_SEC = 1000
private const val SECS_PER_HOUR = 3600
private const val SECS_PER_MIN = 60
private const val TEN_FACTOR = 10

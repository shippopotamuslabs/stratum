package com.stratum.core.ui.component

import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightConversion
import com.stratum.domain.lift.shortLabel
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

fun formatSetPerformance(weightAmount: Double, reps: Int, partialReps: Int? = null): String {
    val repsLabel = formatRepsWithPartials(reps, partialReps)
    return if (weightAmount == 0.0) {
        repsLabel
    } else {
        "${formatWeightAmount(weightAmount)} × $repsLabel"
    }
}

/**
 * Formats set performance after converting [weight] into [displayUnit].
 * When [includeUnit] is true, appends the short unit label (e.g. `"135 lb × 5"`).
 * Partials render as `5+2` when [partialReps] is greater than zero.
 */
fun formatSetPerformance(
    weight: Weight,
    reps: Int,
    displayUnit: Weight.WeightUnit,
    includeUnit: Boolean = false,
    partialReps: Int? = null,
): String {
    val amount =
        WeightConversion.convertAmount(weight.amount, weight.unit, displayUnit)
    val repsLabel = formatRepsWithPartials(reps, partialReps)
    if (amount == 0.0) return repsLabel
    val amountLabel = formatWeightAmount(amount)
    return if (includeUnit) {
        "$amountLabel ${displayUnit.shortLabel()} × $repsLabel"
    } else {
        "$amountLabel × $repsLabel"
    }
}

/** Full reps, plus `+N` when [partialReps] is greater than zero. */
fun formatRepsWithPartials(reps: Int, partialReps: Int?): String {
    val partials = partialReps?.takeIf { it > 0 }
    return if (partials == null) reps.toString() else "$reps+$partials"
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
 * Converts [amount] from [sourceUnit] into [displayUnit] and formats the number.
 * Blank when the display amount is 0 (same contract as [formatWeightAmount]).
 */
fun formatWeightAmountForDisplay(
    amount: Double,
    sourceUnit: Weight.WeightUnit,
    displayUnit: Weight.WeightUnit,
    assistMode: Boolean = false,
): String {
    val converted = WeightConversion.convertAmount(amount, sourceUnit, displayUnit)
    return formatWeightAmount(converted, assistMode)
}

/**
 * Formats [weight] in [displayUnit]. When [includeUnit] is true, appends `" lb"` / `" kg"`.
 *
 * When [includeUnit] is false, zero follows the [formatWeightAmount] blank-placeholder contract.
 * When [includeUnit] is true, zero renders as `"0 lb"` / `"0 kg"` so labels stay readable.
 */
fun formatWeightForDisplay(
    weight: Weight,
    displayUnit: Weight.WeightUnit,
    assistMode: Boolean = false,
    includeUnit: Boolean = true,
): String {
    val converted = WeightConversion.convertAmount(weight.amount, weight.unit, displayUnit)
    val displayAmount = if (assistMode) kotlin.math.abs(converted) else converted
    if (!includeUnit) {
        return formatWeightAmount(displayAmount)
    }
    val number =
        if (displayAmount == 0.0) {
            "0"
        } else {
            formatWeightAmount(displayAmount).ifEmpty { "0" }
        }
    return "$number ${displayUnit.shortLabel()}"
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

package com.stratum.domain.lift

import kotlin.math.roundToLong

/**
 * A weight-banded increment step. A lift carries an ordered list of these so that, as the
 * working weight grows, the realistic load jump grows too (§2.2). The top tier has a `null`
 * [upperBound] meaning "and above" — e.g.:
 *
 * ```text
 * 0 – 40 lb   → +2.5 lb
 * 40 – 100    → +5
 * 100 – 300   → +10
 * 300+        → +25
 * ```
 *
 * [lowerBound] is inclusive, [upperBound] is exclusive. Tiers are validated as sorted,
 * non-overlapping, and with a positive [increment] by `ValidateIncrementTiersUseCase` before
 * they are persisted. All weights on a tier list share one unit (the lift's unit).
 */
data class IncrementTier(
    val lowerBound: Weight,
    val upperBound: Weight?,
    val increment: Weight,
) {
    init {
        require(increment.amount > 0) { "Increment must be positive" }
    }
}

/**
 * Rounds [weight] to the nearest increment step for the band it falls into. Used by the
 * recommendation engine so live weight suggestions land on realistic load jumps (e.g.
 * `61.35` → `60`, `93.93` → `95`). An empty tier list is a passthrough — no rounding.
 *
 * The matching band is the first tier whose `[lowerBound, upperBound)` range contains the
 * weight; if none matches (the weight is below the lowest band), the first tier is used.
 * Rounding is half-away-from-zero via [roundToLong].
 */
fun List<IncrementTier>.roundToIncrement(weight: Weight): Weight {
    if (isEmpty()) return weight
    val tier = firstOrNull {
        weight.amount >= it.lowerBound.amount &&
            (it.upperBound == null || weight.amount < it.upperBound.amount)
    } ?: first()
    val inc = tier.increment.amount
    if (inc <= 0.0) return weight
    val rounded = (weight.amount / inc).roundToLong() * inc
    return Weight(rounded, weight.unit)
}

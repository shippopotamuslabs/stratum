package com.stratum.domain.lift

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

package com.stratum.domain.lift

import javax.inject.Inject

/**
 * Validates a lift's increment tiers (§2.2). Pure (no I/O). Used by the catalog seeder as a
 * guard against malformed seed data, and unit-tested directly.
 *
 * A tier list is valid when:
 * 1. **Sorted & contiguous** — tiers are ordered by `lowerBound` and each tier's `lowerBound`
 *    equals the previous tier's `upperBound`, so the bands tile without gaps or overlaps.
 * 2. **Mixed units forbidden** — every weight shares one unit; mixing lb and kg on a single
 *    lift makes the bands meaningless.
 * 3. **Open final band** — the last tier's `upperBound` is `null` ("and above"); intermediate
 *    tiers must have a non-null upper bound so the tiling is well-defined.
 * 4. **Non-decreasing increments** — not strictly required by physiology, but a *decreasing*
 *    increment as load grows is almost always a seed-data mistake, so it is rejected.
 *
 * The empty list is valid (a lift with no tiers falls back to a global default in OS-6).
 */
class ValidateIncrementTiersUseCase @Inject constructor() {
    operator fun invoke(tiers: List<IncrementTier>): IncrementTiersValidationResult {
        if (tiers.isEmpty()) return IncrementTiersValidationResult.Valid

        val errors = buildSet {
            val unit = tiers.first().lowerBound.unit
            val allSameUnit = tiers.all { tier ->
                tier.lowerBound.unit == unit &&
                    (tier.upperBound == null || tier.upperBound.unit == unit) &&
                    tier.increment.unit == unit
            }
            if (!allSameUnit) add(IncrementTiersValidationError.MixedUnits)

            // Bands must tile: prev.upperBound == next.lowerBound, in order. The per-tier
            // checks live in [checkTier] (returns false on a structural break) so this loop
            // has a single continuation point.
            var previousUpper: Double? = 0.0
            var previousIncrement: Double = 0.0
            var contiguous = true
            for ((index, tier) in tiers.withIndex()) {
                val (keepGoing, nextUpper, nextIncrement) = checkTier(
                    tier = tier,
                    index = index,
                    last = tiers.lastIndex,
                    previousUpper = previousUpper,
                    previousIncrement = previousIncrement,
                    onError = { error -> this@buildSet.add(error) },
                )
                if (!keepGoing) {
                    contiguous = false
                    break
                }
                previousUpper = nextUpper
                previousIncrement = nextIncrement
            }
            if (!contiguous) add(IncrementTiersValidationError.NotContiguous)
        }

        return if (errors.isEmpty()) {
            IncrementTiersValidationResult.Valid
        } else {
            IncrementTiersValidationResult.Invalid(errors)
        }
    }

    /**
     * Validates one tier against its predecessor. Returns `false` when a structural break
     * (gap, zero-width band, or missing intermediate upper bound) means tiling has failed and
     * the caller should stop; `true` to continue. Band-shape errors are reported via [onError]
     * and still allow iteration to proceed where the problem is non-fatal (open-ended final
     * band, decreasing increment).
     */
    private fun checkTier(
        tier: IncrementTier,
        index: Int,
        last: Int,
        previousUpper: Double?,
        previousIncrement: Double,
        onError: (IncrementTiersValidationError) -> Unit,
    ): Triple<Boolean, Double?, Double> {
        if (tier.lowerBound.amount != previousUpper) {
            return Triple(false, previousUpper, previousIncrement)
        }
        val upper = tier.upperBound
        val isLast = index == last
        if (isLast && upper != null) {
            onError(IncrementTiersValidationError.FinalTierMustBeOpenEnded)
        }
        if (!isLast && upper == null) {
            onError(IncrementTiersValidationError.IntermediateTierMissingUpperBound)
            return Triple(false, previousUpper, previousIncrement)
        }
        if (upper != null && upper.amount <= tier.lowerBound.amount) {
            onError(IncrementTiersValidationError.ZeroWidthBand)
            return Triple(false, previousUpper, previousIncrement)
        }
        if (tier.increment.amount < previousIncrement) {
            onError(IncrementTiersValidationError.IncrementDecreased)
        }
        return Triple(true, upper?.amount, tier.increment.amount)
    }
}

enum class IncrementTiersValidationError {
    MixedUnits,
    NotContiguous,
    ZeroWidthBand,
    IntermediateTierMissingUpperBound,
    FinalTierMustBeOpenEnded,
    IncrementDecreased,
}

sealed interface IncrementTiersValidationResult {
    data object Valid : IncrementTiersValidationResult
    data class Invalid(val errors: Set<IncrementTiersValidationError>) : IncrementTiersValidationResult
}

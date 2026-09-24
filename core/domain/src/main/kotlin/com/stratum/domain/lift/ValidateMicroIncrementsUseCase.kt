package com.stratum.domain.lift

import javax.inject.Inject

/**
 * Validates micro-adjust (adder knob) stops against the major increment tiers. Pure (no I/O).
 *
 * Stored stops are non-zero offsets (e.g. `[5, 10]` displays as `0 / 5 / 10`); knob-off, the
 * bare stack load, is always legal, so any `0` stop is rejected as meaningless — use empty
 * (no adder) instead. A stop list is valid when:
 *
 * 1. **Tiers present** — a knob with no stack is meaningless (bodyweight stays micro-empty).
 * 2. **Mixed units forbidden** — every stop shares the tiers' unit.
 * 3. **Non-negative** — stops are additive offsets, never negative.
 * 4. **Strictly ascending** — sorted with no duplicates.
 * 5. **Within the coarsest step** — every stop is `<=` the largest major increment. Real
 *    knobs span up to the coarsest plate step (e.g. a 0/5/10 spinner on a 15 lb stack),
 *    never beyond it; a larger offset is a tier step, not micro.
 *
 * The empty list is always valid (no adder).
 */
class ValidateMicroIncrementsUseCase @Inject constructor() {
    operator fun invoke(
        tiers: List<IncrementTier>,
        micro: List<Weight>,
    ): MicroIncrementsValidationResult {
        if (micro.isEmpty()) return MicroIncrementsValidationResult.Valid
        if (tiers.isEmpty()) {
            return MicroIncrementsValidationResult.Invalid(
                setOf(MicroIncrementsValidationError.MicroWithoutTiers),
            )
        }

        val errors = buildSet {
            val unit = tiers.first().lowerBound.unit
            if (micro.any { it.unit != unit }) add(MicroIncrementsValidationError.MixedUnits)

            val amounts = micro.map { it.amount }
            if (amounts.any { it < 0.0 }) add(MicroIncrementsValidationError.NegativeStop)
            if (!amounts.zipWithNext().all { (prior, next) -> prior < next }) {
                add(MicroIncrementsValidationError.UnsortedOrDuplicate)
            }
            if (amounts.any { it == 0.0 }) {
                add(MicroIncrementsValidationError.MeaninglessZero)
            }
            val largestIncrement = tiers.maxOf { it.increment.amount }
            if (amounts.any { it > largestIncrement }) {
                add(MicroIncrementsValidationError.ExceedsLargestIncrement)
            }
        }

        return if (errors.isEmpty()) {
            MicroIncrementsValidationResult.Valid
        } else {
            MicroIncrementsValidationResult.Invalid(errors)
        }
    }
}

enum class MicroIncrementsValidationError {
    MixedUnits,
    NegativeStop,
    UnsortedOrDuplicate,
    MeaninglessZero,
    ExceedsLargestIncrement,
    MicroWithoutTiers,
}

sealed interface MicroIncrementsValidationResult {
    data object Valid : MicroIncrementsValidationResult
    data class Invalid(val errors: Set<MicroIncrementsValidationError>) : MicroIncrementsValidationResult
}

/** Thrown when a set-tiers use case rejects micro stops. */
class MicroIncrementsInvalidException(
    val errors: Set<MicroIncrementsValidationError>,
) : Exception("Invalid micro increments: $errors")

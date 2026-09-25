package com.stratum.domain.lift

import kotlin.math.abs

/**
 * Absolute residual tolerance for lattice fit checks (same unit as the weight amount).
 *
 * Chosen so 62.5 on a 5-step lattice is clearly off-lattice while floating noise near a
 * true plate (e.g. 100/3 rounding) stays on-lattice.
 */
const val LATTICE_EPSILON: Double = 0.05

/**
 * Distance from [weight] to the nearest on-lattice amount under these tiers.
 *
 * Uses [roundToIncrement] (including [LoadType] sign handling for assisted loads). Empty
 * tiers have no lattice → [Double.POSITIVE_INFINITY]. [LoadType.Bodyweight] always yields
 * `0.0` (no stack signal). Non-empty [micro] additionally admits `base + adder stop`
 * points (knob-off included), so adder-exact weights have ~zero residual.
 */
fun List<IncrementTier>.residual(
    weight: Weight,
    loadType: LoadType = LoadType.Weighted,
    micro: List<Weight> = emptyList(),
): Double {
    if (loadType == LoadType.Bodyweight) return 0.0
    if (isEmpty()) return Double.POSITIVE_INFINITY
    val rounded = roundToIncrement(weight, loadType, micro)
    return abs(weight.amount - rounded.amount)
}

/**
 * Whether [weight] lands on the increment lattice defined by these tiers.
 *
 * - Empty tiers → `false` (no lattice; callers should skip proposal signals).
 * - [LoadType.Bodyweight] → `true` (never a stack anomaly; callers should still skip).
 * - [LoadType.Assisted] uses magnitude-based band lookup via [roundToIncrement].
 * - Non-empty [micro] admits `base + adder stop` points as on-lattice.
 */
fun List<IncrementTier>.isOnLattice(
    weight: Weight,
    loadType: LoadType = LoadType.Weighted,
    epsilon: Double = LATTICE_EPSILON,
    micro: List<Weight> = emptyList(),
): Boolean {
    if (loadType == LoadType.Bodyweight) return true
    if (isEmpty()) return false
    return residual(weight, loadType, micro) <= epsilon
}

/**
 * Indexes of bands whose steps do not divide evenly with at least one of [samplesLb]
 * (raw storage-pound magnitudes, e.g. a proposal's logged session weights). Powers the
 * increments editor's non-blocking step warning: callers highlight the returned bands
 * but still allow saving. Samples at or below zero, and samples outside every band,
 * are ignored. Band containment and the remainder tie use [epsilon] (default
 * [LATTICE_EPSILON]) so the warning agrees with proposal analysis.
 */
fun List<IncrementTier>.warnedIndexesForSamples(
    samplesLb: List<Double>,
    epsilon: Double = LATTICE_EPSILON,
): Set<Int> {
    if (samplesLb.isEmpty() || isEmpty()) return emptySet()
    return samplesLb
        .filter { it > 0.0 }
        .mapNotNull { sample -> warnedIndexForSample(sample, epsilon) }
        .toSet()
}

/**
 * [warnedIndexesForSamples], excluding samples that land on `base + adder stop`
 * ([micro], storage pounds — micro converts to the tiers unit at the rounding
 * boundary). Adder-exact samples are knob use, not band misses, so the adder
 * never trips the warning. Empty [micro] is the plain major-lattice path.
 */
fun List<IncrementTier>.warnedIndexesForSamples(
    samplesLb: List<Double>,
    micro: List<Weight>,
    epsilon: Double = LATTICE_EPSILON,
): Set<Int> {
    if (samplesLb.isEmpty() || isEmpty()) return emptySet()
    if (micro.isEmpty()) return warnedIndexesForSamples(samplesLb, epsilon)
    val signal =
        samplesLb.filter { sample ->
            !isOnLattice(Weight(sample, Weight.WeightUnit.Lb), micro = micro, epsilon = epsilon)
        }
    if (signal.isEmpty()) return emptySet()
    return warnedIndexesForSamples(signal, epsilon)
}

private fun List<IncrementTier>.warnedIndexForSample(sample: Double, epsilon: Double): Int? {
    val index =
        indexOfFirst { tier ->
            val upper = tier.upperBound
            sample >= tier.lowerBound.amount - epsilon &&
                (upper == null || sample < upper.amount - epsilon)
        }
    if (index < 0) return null
    val tier = this[index]
    val step = tier.increment.amount
    if (step <= 0.0) return null
    val remainder = abs((sample - tier.lowerBound.amount) % step)
    return index.takeIf { minOf(remainder, step - remainder) > epsilon }
}

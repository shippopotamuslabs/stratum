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
 * `0.0` (no stack signal).
 */
fun List<IncrementTier>.residual(
    weight: Weight,
    loadType: LoadType = LoadType.Weighted,
): Double {
    if (loadType == LoadType.Bodyweight) return 0.0
    if (isEmpty()) return Double.POSITIVE_INFINITY
    val rounded = roundToIncrement(weight, loadType)
    return abs(weight.amount - rounded.amount)
}

/**
 * Whether [weight] lands on the increment lattice defined by these tiers.
 *
 * - Empty tiers → `false` (no lattice; callers should skip proposal signals).
 * - [LoadType.Bodyweight] → `true` (never a stack anomaly; callers should still skip).
 * - [LoadType.Assisted] uses magnitude-based band lookup via [roundToIncrement].
 */
fun List<IncrementTier>.isOnLattice(
    weight: Weight,
    loadType: LoadType = LoadType.Weighted,
    epsilon: Double = LATTICE_EPSILON,
): Boolean {
    if (loadType == LoadType.Bodyweight) return true
    if (isEmpty()) return false
    return residual(weight, loadType) <= epsilon
}

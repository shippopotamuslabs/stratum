package com.stratum.domain.lift

import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import kotlin.math.abs

/**
 * Typo guard: a weight more than this multiple of the max of *other* sets on the same lift
 * in the session is treated as a mistype (e.g. 625 vs 62.5), not a stack signal.
 */
const val EQUIPMENT_INCREMENT_TYPO_MULTIPLIER: Double = 3.0


data class LatticeScreening(
    val usableWeights: List<Weight>,
    val residualWeights: List<Weight>,
)

/**
 * Splits [weights] into usable work weights and the off-lattice residual subset:
 * typo-guard ([filterTypoWeights]), near-zero skip, then [isOnLattice]
 * ([LoadType]-aware). Bodyweight lifts and empty tier lists screen empty (no signal).
 */

fun screenLatticeWeights(
    weights: List<Weight>,
    tiers: List<IncrementTier>,
    loadType: LoadType,
    epsilon: Double = LATTICE_EPSILON,
    micro: List<Weight> = emptyList(),
): LatticeScreening {
    if (loadType == LoadType.Bodyweight || tiers.isEmpty()) {
        return LatticeScreening(emptyList(), emptyList())
    }
    val usable =
        filterTypoWeights(weights, epsilon).filter { weight ->
            abs(weight.amount) > epsilon
        }
    return LatticeScreening(
        usableWeights = usable,
        residualWeights =
            usable.filter { weight ->
                !tiers.isOnLattice(weight, loadType, epsilon, micro)
            },
    )
}

/**
 * Builds an anomaly for one lift's sets, or `null` when the lift is not an off-lattice signal.
 */

fun filterTypoWeights(
    weights: List<Weight>,
    epsilon: Double = LATTICE_EPSILON,
): List<Weight> {
    if (weights.size < 2) return weights
    return weights.filterIndexed { index, weight ->
        val amount = abs(weight.amount)
        if (amount <= epsilon) return@filterIndexed true
        val others =
            weights.mapIndexedNotNull { i, w ->
                if (i == index) null else abs(w.amount)
            }
        val peerMax = others.maxOrNull() ?: return@filterIndexed true
        if (peerMax <= epsilon) {
            // No non-zero peer baseline → drop lone spikes among zeros.
            return@filterIndexed false
        }
        amount <= peerMax * EQUIPMENT_INCREMENT_TYPO_MULTIPLIER + epsilon
    }
}

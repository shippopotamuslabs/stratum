package com.stratum.domain.lift

import kotlin.math.abs

/**
 * Live, per-lift off-lattice signal for the workout screen's "Adjust Equipment" chip.
 *
 * Same semantics as [anomalyForLift] (which powers the post-workout
 * [AnalyzeSessionEquipmentProposalsUseCase]): bodyweight lifts never signal, empty tier
 * lists never signal, near-zero weights are ignored, typo-magnitude outliers are dropped
 * via [filterTypoWeights], and the remaining work weights must each land on the lattice
 * ([isOnLattice], [LoadType]-aware) for the chip to stay hidden. Any residual weight
 * shows the chip.
 *
 * Unlike the post-workout path there is no sparse-seed suppression or ranking here: the
 * input is only the sets logged so far this session, and a single genuine off-lattice
 * set at the machine is worth one quiet chip.
 */
fun shouldShowLiveEquipmentAdjust(
    loggedWeights: List<Weight>,
    lift: Lift,
    epsilon: Double = LATTICE_EPSILON,
): Boolean {
    val effectiveTiers = lift.incrementTiers
    if (lift.loadType == LoadType.Bodyweight || effectiveTiers.isEmpty()) {
        return false
    }
    val usable =
        filterTypoWeights(loggedWeights, epsilon).filter { weight ->
            abs(weight.amount) > epsilon
        }
    if (usable.isEmpty()) return false
    return usable.any { weight ->
        !effectiveTiers.isOnLattice(weight, lift.loadType, epsilon)
    }
}

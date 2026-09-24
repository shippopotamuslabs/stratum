package com.stratum.domain.lift

/**
 * Live, per-lift off-lattice signal for the workout screen's "Adjust Equipment" chip.
 *
 * Same pipeline as [anomalyForLift] (which powers the post-workout
 * [AnalyzeSessionEquipmentProposalsUseCase]) via the shared [screenLatticeWeights]:
 * bodyweight skip, empty-tiers skip, typo-guard, near-zero skip, [isOnLattice].
 * Any residual weight shows the chip.
 *
 * Unlike the post-workout path there is no sparse-seed suppression or ranking here: the
 * input is only the sets logged so far this session, and a single genuine off-lattice
 * set at the machine is worth one quiet chip.
 */
fun shouldShowLiveEquipmentAdjust(
    loggedWeights: List<Weight>,
    lift: Lift,
    epsilon: Double = LATTICE_EPSILON,
): Boolean =
    screenLatticeWeights(
        weights = loggedWeights,
        tiers = lift.incrementTiers,
        loadType = lift.loadType,
        epsilon = epsilon,
        micro = lift.microIncrements,
    ).residualWeights.isNotEmpty()

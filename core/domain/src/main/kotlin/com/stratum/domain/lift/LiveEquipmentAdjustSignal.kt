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
 *
 * The lattice is explicit ([steps]) rather than read off a [Lift]: the equipment in
 * use can differ from the lift's linked equipment after a live swap (STR-128), so
 * every caller resolves the in-use equipment's [EffectiveSteps] itself.
 */
fun shouldShowLiveEquipmentAdjust(
    loggedWeights: List<Weight>,
    steps: EffectiveSteps,
    loadType: LoadType,
    epsilon: Double = LATTICE_EPSILON,
): Boolean =
    screenLatticeWeights(
        weights = loggedWeights,
        tiers = steps.tiers,
        loadType = loadType,
        epsilon = epsilon,
        micro = steps.microIncrements,
    ).residualWeights.isNotEmpty()

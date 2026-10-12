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
    shouldShowLiveEquipmentAdjust(
        loggedWeights = loggedWeights,
        steps =
            EffectiveSteps(
                tiers = lift.incrementTiers,
                microIncrements = lift.microIncrements,
            ),
        loadType = lift.loadType,
        epsilon = epsilon,
    )

/**
 * Steps-explicit variant for callers whose equipment in use differs from the lift's
 * linked equipment (a live equipment swap, STR-128): the lattice is the *resolved*
 * equipment's [steps], not the catalog lift's defaults.
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

package com.stratum.domain.solver

/**
 * Tiered objective weights. Each tier is separated by ~10× so lower tiers cannot
 * compensate for violations in higher tiers (lexicographic behavior in one sum).
 *
 * Role/specialization minimums and min-sets-per-active-slot are HARD constraints
 * (see `addRoleMinimumConstraints` / `addMinSetsPerActiveSlot`), so they no longer
 * appear as objective terms. Fill/imbalance sit strictly below `SPECIALIZED_MEV_SHORTFALL`
 * so volume targets win over filling (G4). Specialize aim ≥ maintenance aim so
 * specialized muscles are pushed toward MRV (G6). `SLOT_ACTIVATION_COUNT` consolidates
 * equal-credit optima toward fewer, higher-set lifts (G5).
 */
internal object SynthesisObjectiveWeights {
    const val RECOVERY_PRIORITY = 1_000_000L
    const val CONSECUTIVE_TRAINING_DAY_EXPOSURE = 5_000_000L
    const val RECOVERY_GENERAL = 100_000L
    const val SPECIALIZED_MEV_SHORTFALL = 10_000L
    const val CO_SPECIALIZATION_BALANCE = 1_000L
    /** Pull maintenance antagonist pairs (chest/back, quad/ham) toward parity. Sits just above
     *  [SESSION_FILL_SHORTFALL] so a small chest/back gap (e.g. 2 sets) outweighs filling one
     *  more set, but stays below the 2000 aim tier to respect lexicographic ordering (G13). */
    const val ANTAGONIST_PAIR_CLOSENESS = 5_100L
    /** Penalize the joint-risk direction: chest > back, quad > ham. */
    const val ANTAGONIST_WRONG_WAY_EXCESS = 8_000L
    /** Mild penalty when the preferred direction is heavier: back > chest, ham > quad. */
    const val ANTAGONIST_RIGHT_WAY_EXCESS = 300L
    /** Specialized muscles are pushed toward MRV aim — ≥ maintenance so spec wins (G6). */
    const val SPECIALIZE_AIM_DEVIATION = 2_000L
    const val MINIMUM_VOLUME_AIM_DEVIATION = 80L
    /** Maintenance PV targets must compete with session-fill and specialization slacks. */
    const val MAINTENANCE_AIM_DEVIATION = 2_000L
    /** Consolidation: penalize each active slot so ties collapse to fewer, higher-set lifts (G5). */
    const val SLOT_ACTIVATION_COUNT = 300L
    const val OPTIONAL_SLOT_INACTIVE_PREFERENCE = 20L
    /** Mild discouragement of direct VerticalPress sets when AnteriorDeltoid is Maintenance — the
     *  anterior aim is reachable via chest-press secondaries (0.5/set), so direct overhead work
     *  (1.0/set) is coach-unwanted multi-aim churning (G11). Sits between inactive-preference and
     *  slot-activation so it only biases tie-breaks, never overrides volume aims. */
    const val MAINTENANCE_ANTERIOR_DIRECT_DISCOURAGE = 150L
    /** Push/pull balance breaks ties consolidation cares about (G10). Below [ANTAGONIST_PAIR_CLOSENESS]. */
    const val PUSH_PULL_IMBALANCE = 200L
    /** Session fill sits below MEV so volume targets win over filling (G4). */
    const val SESSION_FILL_SHORTFALL = 5_000L
    /** Session set balance sits below MEV (G4). */
    const val SESSION_SET_IMBALANCE = 2_500L
}

package com.stratum.domain.solver

/**
 * Tiered objective weights. Each tier is separated by ~10× so lower tiers cannot
 * compensate for violations in higher tiers (lexicographic behavior in one sum).
 */
internal object SynthesisObjectiveWeights {
    const val RECOVERY_PRIORITY = 1_000_000L
    const val CONSECUTIVE_TRAINING_DAY_EXPOSURE = 5_000_000L
    const val RECOVERY_GENERAL = 100_000L
    const val SPECIALIZED_MEV_SHORTFALL = 10_000L
    const val CO_SPECIALIZATION_BALANCE = 1_000L
    /** Pull maintenance antagonist pairs (chest/back, quad/ham) toward parity. */
    const val ANTAGONIST_PAIR_CLOSENESS = 800L
    /** Penalize the joint-risk direction: chest > back, quad > ham. */
    const val ANTAGONIST_WRONG_WAY_EXCESS = 8_000L
    /** Mild penalty when the preferred direction is heavier: back > chest, ham > quad. */
    const val ANTAGONIST_RIGHT_WAY_EXCESS = 300L
    const val SPECIALIZE_AIM_DEVIATION = 100L
    const val MINIMUM_VOLUME_AIM_DEVIATION = 80L
    /** Maintenance PV targets must compete with session-fill and specialization slacks. */
    const val MAINTENANCE_AIM_DEVIATION = 2_000L
    const val ROLE_MINIMUM_SHORTFALL = 50L
    const val OPTIONAL_SLOT_INACTIVE_PREFERENCE = 20L
    const val PUSH_PULL_IMBALANCE = 1L
    const val SESSION_FILL_SHORTFALL = 50_000L
    const val SESSION_SET_IMBALANCE = 25_000L
}

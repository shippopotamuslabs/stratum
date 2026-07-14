package com.stratum.domain.solver

/**
 * Tiered objective weights. Each tier is separated by ~10× so lower tiers cannot
 * compensate for violations in higher tiers (lexicographic behavior in one sum).
 *
 * IMPORTANT — units & scale invariance: credit-based slacks (aims, weekly mins, antagonist,
 * co-specialization) are in SCALED credits and their weights are per-scaled-credit, so they are
 * scale-invariant. Set-based slacks (session fill, set balance, activation, rear-delt direct,
 * frequency shortfall) are in raw sets / discrete hits; their weights are MULTIPLIED by
 * `coefficients.scale` at the term-addition site in `ProgramSynthesisModel` so they remain
 * proportional to the credit-denominated tiers as SCALE changes.
 *
 * Role/specialization minimums and min-sets-per-active-slot are HARD constraints
 * (see `addRoleMinimumConstraints` / `addMinSetsPerActiveSlot`), so they no longer
 * appear as objective terms.
 *
 * RE-TUNING (2026-07, study `stratum_solver_weights_v3_day_prior`, stopped early at 12/20):
 * Product-weighted 61-scenario suite (~69% 4–5 day, ~18% 6-day, ~13% 3-day) with frequency +
 * primary-aware recovery loss. Trial 5 beat the production seed by ~4.5% (892,643 vs 934,288);
 * trials 6–11 did not improve. Applied in full (secondary-only recovery nuance already landed,
 * so the prior trial-3 full-vector Glute regression path is less relevant).
 *
 * Notable shifts vs pre-retune defaults:
 *   - [FREQUENCY_SHORTFALL] 10,500→12,000
 *   - [SLOT_ACTIVATION_COUNT] 1,000→650 (easier multi-hit frequency)
 *   - [LOWER_ANCHOR_ACTIVATION_BONUS] −100→−550 (net lower-anchor cost 650−550=+100)
 *   - [MAINTENANCE_AIM_DEVIATION] 2,300→3,700; [SPECIALIZE_AIM_DEVIATION] 3,900→4,900
 *   - [SESSION_FILL_SHORTFALL] 5,000→6,500; [SESSION_SET_IMBALANCE] 3,500→1,900
 *   - [LOWER_BODY_MAINTENANCE_PARITY] 2,200→500 (weakened; upper parity slightly up)
 */
object SynthesisObjectiveWeights {
    private var overrides: Map<String, Long>? = null

    fun setOverrides(newOverrides: Map<String, Long>?) {
        overrides = newOverrides
    }

    private fun getWeight(key: String, default: Long): Long {
        return overrides?.get(key) ?: default
    }

    val RECOVERY_PRIORITY: Long get() = getWeight("RECOVERY_PRIORITY", 3_300_000L)
    val RECOVERY_GENERAL: Long get() = getWeight("RECOVERY_GENERAL", 300_000L)
    val SPECIALIZED_MIN_SHORTFALL: Long get() = getWeight("SPECIALIZED_MIN_SHORTFALL", 10_000L)
    val CO_SPECIALIZATION_BALANCE: Long get() = getWeight("CO_SPECIALIZATION_BALANCE", 1_000L)
    /**
     * Pull maintenance antagonist pairs (chest/back, quad/ham) toward parity. Sits just above
     * [SESSION_FILL_SHORTFALL] so a small chest/back gap outweighs filling one more set.
     *
     * NOTE (audit §2.1): this is 5,100/credit-scaled — i.e. it sits ABOVE the aim tier for many
     * configurations. Intentional: aggressively balancing chest≈back is judged more valuable
     * than each muscle hitting its own aim exactly.
     */
    val ANTAGONIST_PAIR_CLOSENESS: Long get() = getWeight("ANTAGONIST_PAIR_CLOSENESS", 5_100L)
    /** Penalize the joint-risk direction: chest > back, quad > ham. */
    val ANTAGONIST_WRONG_WAY_EXCESS: Long get() = getWeight("ANTAGONIST_WRONG_WAY_EXCESS", 8_000L)
    /** Mild penalty when the preferred direction is heavier: back > chest, ham > quad. */
    val ANTAGONIST_RIGHT_WAY_EXCESS: Long get() = getWeight("ANTAGONIST_RIGHT_WAY_EXCESS", 300L)
    /**
     * Pull symmetric pairs (bicep/tricep) toward parity with no directional preference.
     */
    val SYMMETRIC_PAIR_CLOSENESS: Long get() = getWeight("SYMMETRIC_PAIR_CLOSENESS", 1_500L)
    /**
     * Upper-body maintenance group pairwise parity
     * {Chest, Back, Bicep, Tricep, LateralDeltoid}.
     */
    val UPPER_BODY_MAINTENANCE_PARITY: Long get() = getWeight("UPPER_BODY_MAINTENANCE_PARITY", 800L)
    /**
     * Lower-body maintenance group pairwise parity {Quad, Hamstring, Calf}.
     */
    val LOWER_BODY_MAINTENANCE_PARITY: Long get() = getWeight("LOWER_BODY_MAINTENANCE_PARITY", 500L)
    /**
     * Specialized muscles pushed toward RC aim — above [MAINTENANCE_AIM_DEVIATION] at these
     * tuned weights (G6).
     */
    val SPECIALIZE_AIM_DEVIATION: Long get() = getWeight("SPECIALIZE_AIM_DEVIATION", 4_900L)
    val MINIMUM_VOLUME_AIM_DEVIATION: Long get() = getWeight("MINIMUM_VOLUME_AIM_DEVIATION", 80L)
    /** Maintenance PV targets. */
    val MAINTENANCE_AIM_DEVIATION: Long get() = getWeight("MAINTENANCE_AIM_DEVIATION", 3_700L)
    /**
     * Soft shortfall when a non-de-emphasized major muscle is hit fewer than the preferred weekly
     * frequency (2 meaningful sessions when the cycle has ≥3 training days).
     */
    val FREQUENCY_SHORTFALL: Long get() = getWeight("FREQUENCY_SHORTFALL", 12_000L)
    /** Consolidation: penalize each active slot (fewer, higher-set lifts). */
    val SLOT_ACTIVATION_COUNT: Long get() = getWeight("SLOT_ACTIVATION_COUNT", 650L)
    /**
     * Lower-body anchor activation bonus. Net cost =
     * [SLOT_ACTIVATION_COUNT] + [LOWER_ANCHOR_ACTIVATION_BONUS] = 650 − 550 = +100.
     */
    val LOWER_ANCHOR_ACTIVATION_BONUS: Long get() = getWeight("LOWER_ANCHOR_ACTIVATION_BONUS", -550L)
    val PUSH_PULL_IMBALANCE: Long get() = getWeight("PUSH_PULL_IMBALANCE", 200L)
    /** Session fill shortfall — structural gate. */
    val SESSION_FILL_SHORTFALL: Long get() = getWeight("SESSION_FILL_SHORTFALL", 6_500L)
    val SESSION_SET_IMBALANCE: Long get() = getWeight("SESSION_SET_IMBALANCE", 1_900L)
    val MAINTENANCE_POSTERIOR_DIRECT_DISCOURAGE: Long get() =
        getWeight("MAINTENANCE_POSTERIOR_DIRECT_DISCOURAGE", 5_500L)
    val MAINTENANCE_POSTERIOR_AIM_DEVIATION: Long get() =
        getWeight("MAINTENANCE_POSTERIOR_AIM_DEVIATION", 500L)
}

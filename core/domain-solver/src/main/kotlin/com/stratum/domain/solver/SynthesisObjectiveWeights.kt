package com.stratum.domain.solver

/**
 * Tiered objective weights. Each tier is separated by ~10× so lower tiers cannot
 * compensate for violations in higher tiers (lexicographic behavior in one sum).
 *
 * IMPORTANT — units & scale invariance: credit-based slacks (aims, weekly mins, antagonist,
 * co-specialization) are in SCALED credits and their weights are per-scaled-credit, so they are
 * scale-invariant. Set-based slacks (session fill, set balance, activation, rear-delt direct) are
 * in raw sets; their weights are MULTIPLIED by `coefficients.scale` at the term-addition site in
 * `ProgramSynthesisModel` so they remain proportional to the credit-denominated tiers as SCALE
 * changes. So a per-primary-set cost is comparable across both families: one primary set = SCALE
 * scaled credits, so a 2,000/scaled-credit aim weight costs 2,000 per primary set, and a 5,000/set
 * fill weight costs 5,000 × SCALE per primary-set-equivalent of fill — both move together when
 * SCALE changes.
 *
 * Role/specialization minimums and min-sets-per-active-slot are HARD constraints
 * (see `addRoleMinimumConstraints` / `addMinSetsPerActiveSlot`), so they no longer
 * appear as objective terms.
 */
internal object SynthesisObjectiveWeights {
    const val RECOVERY_PRIORITY = 1_000_000L
    const val RECOVERY_GENERAL = 100_000L
    const val SPECIALIZED_MIN_SHORTFALL = 10_000L
    const val CO_SPECIALIZATION_BALANCE = 1_000L
    /**
     * Pull maintenance antagonist pairs (chest/back, quad/ham) toward parity. Sits just above
     * [SESSION_FILL_SHORTFALL] so a small chest/back gap outweighs filling one more set.
     *
     * NOTE (audit §2.1): this is 5,100/credit-scaled — i.e. it sits ABOVE the 2,000/credit-scaled
     * aim tier, not below it. The original "stays below the 2000 aim tier (G13)" comment was
     * arithmetically false. This is intentional tuning: aggressively balancing chest≈back is
     * judged more valuable than each muscle hitting its own aim exactly. If aims should win,
     * demote this below [MAINTENANCE_AIM_DEVIATION].
     */
    const val ANTAGONIST_PAIR_CLOSENESS = 5_100L
    /** Penalize the joint-risk direction: chest > back, quad > ham. */
    const val ANTAGONIST_WRONG_WAY_EXCESS = 8_000L
    /** Mild penalty when the preferred direction is heavier: back > chest, ham > quad. */
    const val ANTAGONIST_RIGHT_WAY_EXCESS = 300L
    /**
     * Specialized muscles are pushed toward RC aim — strictly greater than [MAINTENANCE_AIM_DEVIATION]
     * (G6) so specialized muscles win ties under time pressure rather than leaving it to noise.
     */
    const val SPECIALIZE_AIM_DEVIATION = 2_500L
    const val MINIMUM_VOLUME_AIM_DEVIATION = 80L
    /** Maintenance PV targets must compete with session-fill and specialization slacks. */
    const val MAINTENANCE_AIM_DEVIATION = 2_000L
    /** Consolidation: penalize each active slot so ties collapse to fewer, higher-set lifts (G5). */
    const val SLOT_ACTIVATION_COUNT = 300L
    /** Push/pull balance breaks ties consolidation cares about (G10). Below [ANTAGONIST_PAIR_CLOSENESS]. */
    const val PUSH_PULL_IMBALANCE = 200L
    /**
     * Session fill. NOTE (audit §2.2): at 5,000/set this EXCEEDS the effective maintenance-aim cost
     * (2,000/credit-scaled), so when a session is underfilled the solver pulls maintenance muscles UP
     * from PV toward their maintenance hard ceiling to fill it. This is the intended "push toward PV but not
     * RC" behavior — the maintenance ceiling (`WeeklyVolumeTarget.max` for maintenance) is enforced as a hard
     * constraint by `addWeeklyVolumeCeilings`, so volume never exceeds the maintenance max. The original G4 comment ("volume
     * targets win over filling") described the opposite and was wrong.
     */
    const val SESSION_FILL_SHORTFALL = 5_000L
    /** Session set balance sits below weekly min (G4). */
    const val SESSION_SET_IMBALANCE = 2_500L
    /**
     * G14: Per-set discouragement on direct rear-delt work when PosteriorDeltoid is Maintenance.
     * Posterior gets partial coverage from row secondaries, so direct work competes with
     * bicep/tricep/lateral for limited session time. Multiplied by `coefficients.scale` at the
     * term site so it stays proportional to the per-set posterior-under-aim benefit (which is in
     * scaled credits) — without that scaling, raising SCALE would flip the rear-delt economics and
     * the solver would over-fill posterior. Sits above [SLOT_ACTIVATION_COUNT] (so the solver prefers
     * arms/lateral first) but the scaled value stays below [MAINTENANCE_AIM_DEVIATION] per primary
     * set, so it never fully blocks rear-delt — if secondaries can't reach PV, the solver still
     * activates rear-delt rather than take the larger aim-deviation penalty.
     */
    const val MAINTENANCE_POSTERIOR_DIRECT_DISCOURAGE = 5_500L
    /** G14: Reduced aim-deviation weight for maintenance PosteriorDeltoid — the PV aim is partially
     *  met by row secondaries, so the solver should tolerate a shortfall from direct work
     *  rather than burning session time that bis/tris/lateral should win. Sits above
     *  [SLOT_ACTIVATION_COUNT] but below [MAINTENANCE_AIM_DEVIATION] and far below
     *  [MAINTENANCE_POSTERIOR_DIRECT_DISCOURAGE] so direct work is always a net cost. */
    const val MAINTENANCE_POSTERIOR_AIM_DEVIATION = 500L
}

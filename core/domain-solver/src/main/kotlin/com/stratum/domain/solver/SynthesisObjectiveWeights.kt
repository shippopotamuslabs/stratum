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
 *
 * RE-TUNING (57-scenario benchmark suite, two-layer loss): the 10 tunable defaults below were
 * re-optimized via Optuna TPE against the 57-scenario benchmark suite in `ProgramSynthesisAcceptanceTest`
 * (cosine/Jaccard-diverse splits across 3/4/5/6-day programs and all specialization presets). The
 * benchmark loss uses a two-layer structure: a session-fill gate (dominant — underfilled sessions
 * are structurally broken regardless of volume distribution) and a volume-quality layer (floor-anchored
 * quadratic below MV, linear toward PV above). The winner (trial 18) reduced benchmark loss 31% vs
 * the prior hand-tuned defaults. Notable shifts the optimizer discovered:
 *   - [SPECIALIZE_AIM_DEVIATION] up 30% (3,000→3,900), [MAINTENANCE_AIM_DEVIATION] down 42% (4,000→2,300):
 *     specialized muscles win volume ties more decisively (the G6 "specialized wins" intent).
 *   - [UPPER_BODY_MAINTENANCE_PARITY] down 81% (3,200→600), [LOWER_BODY_MAINTENANCE_PARITY] down 56%
 *     (5,000→2,200): the prior parity weights forced muscles toward equal volume at the cost of
 *     each hitting its own PV aim — weakening them lets each muscle find its individual target.
 *   - [SLOT_ACTIVATION_COUNT] up to 1,000 (from 50): much stronger consolidation — fewer, higher-set
 *     lifts. The session-fill gate (in the benchmark loss) ensures this doesn't collapse sessions.
 *   - [LOWER_ANCHOR_ACTIVATION_BONUS] demoted to -100 (from -250): with SLOT_ACTIVATION_COUNT at
 *     1,000 the net lower-anchor cost is now +900 (penalty), but SESSION_FILL_SHORTFALL at 5,000
 *     ensures lower-body compounds still appear to fill session time.
 * The 11 coach-quality acceptance tests (recovery gaps, consolidation shape, session set band,
 * specialization honored, maintenance MV floor, antagonist balance) all pass at these weights.
 */
object SynthesisObjectiveWeights {
    private var overrides: Map<String, Long>? = null

    fun setOverrides(newOverrides: Map<String, Long>?) {
        overrides = newOverrides
    }

    private fun getWeight(key: String, default: Long): Long {
        return overrides?.get(key) ?: default
    }

    val RECOVERY_PRIORITY: Long get() = getWeight("RECOVERY_PRIORITY", 3_500_000L)
    val RECOVERY_GENERAL: Long get() = getWeight("RECOVERY_GENERAL", 350_000L)
    val SPECIALIZED_MIN_SHORTFALL: Long get() = getWeight("SPECIALIZED_MIN_SHORTFALL", 10_000L)
    val CO_SPECIALIZATION_BALANCE: Long get() = getWeight("CO_SPECIALIZATION_BALANCE", 1_000L)
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
    val ANTAGONIST_PAIR_CLOSENESS: Long get() = getWeight("ANTAGONIST_PAIR_CLOSENESS", 5_100L)
    /** Penalize the joint-risk direction: chest > back, quad > ham. */
    val ANTAGONIST_WRONG_WAY_EXCESS: Long get() = getWeight("ANTAGONIST_WRONG_WAY_EXCESS", 8_000L)
    /** Mild penalty when the preferred direction is heavier: back > chest, ham > quad. */
    val ANTAGONIST_RIGHT_WAY_EXCESS: Long get() = getWeight("ANTAGONIST_RIGHT_WAY_EXCESS", 300L)
    /**
     * Pull symmetric pairs (bicep/tricep) toward parity with no directional preference. Sits BELOW
     * [MAINTENANCE_AIM_DEVIATION] so each muscle hitting its own PV aim wins over exact parity —
     * "roughly even" should not veto a both-at-aim solution that's slightly uneven. Both directions
     * are penalized equally (no wrong-way/right-way split). Above [PUSH_PULL_IMBALANCE] so arm parity
     * outweighs push/pull consolidation ties.
     */
    val SYMMETRIC_PAIR_CLOSENESS: Long get() = getWeight("SYMMETRIC_PAIR_CLOSENESS", 1_500L)
    /**
     * Pull the upper-body maintenance group {Chest, Back, Bicep, Tricep, LateralDeltoid} toward pairwise
     * parity so no single muscle (notably LateralDeltoid, which has no secondary-credit path and thus
     * floats to its maintenance ceiling under session-fill pressure) outruns the group. Same tier as
     * [SYMMETRIC_PAIR_CLOSENESS] so each pair in the group is weighed comparably to the bicep/tricep
     * pair. Per-pair: an outlier feels 4× this weight (one pair per peer); strong enough to reshape the
     * maintenance distribution but well below recovery and hard ceilings. Only fires when both muscles
     * are Maintenance (see gating in `ProgramSynthesisModel.addUpperBodyMaintenanceParity`).
     */
    val UPPER_BODY_MAINTENANCE_PARITY: Long get() = getWeight("UPPER_BODY_MAINTENANCE_PARITY", 600L)
    /**
     * Pull the lower-body maintenance group {Quad, Hamstring, Calf} toward pairwise parity so no single
     * muscle — notably Calf, which has no parity pressure and accumulates incidental secondary credit
     * from ~78% of SquatPattern lifts (~0.39/set) and fast 24h-isolation recovery — outruns the group.
     * Same tier as [SYMMETRIC_PAIR_CLOSENESS] and [UPPER_BODY_MAINTENANCE_PARITY] so each leg pair is
     * weighed comparably to the bicep/tricep pair. The Quad↔Hamstring pair stacks with the antagonist
     * rule ([ANTAGONIST_PAIR_CLOSENESS] = 5,100), matching the upper-body precedent where Chest/Back
     * sit in both rules; Calf↔Quad and Calf↔Hamstring are the pairs this term uniquely anchors. Per-pair:
     * an outlier feels up to 2× this weight (two peers). Only fires when both muscles are Maintenance
     * (see gating in `ProgramSynthesisModel.addLowerBodyMaintenanceParity`).
     */
    val LOWER_BODY_MAINTENANCE_PARITY: Long get() = getWeight("LOWER_BODY_MAINTENANCE_PARITY", 2_200L)
    /**
     * Specialized muscles are pushed toward RC aim — strictly greater than [MAINTENANCE_AIM_DEVIATION]
     * (G6) so specialized muscles win ties under time pressure rather than leaving it to noise.
     */
    val SPECIALIZE_AIM_DEVIATION: Long get() = getWeight("SPECIALIZE_AIM_DEVIATION", 3_900L)
    val MINIMUM_VOLUME_AIM_DEVIATION: Long get() = getWeight("MINIMUM_VOLUME_AIM_DEVIATION", 80L)
    /** Maintenance PV targets must compete with session-fill and specialization slacks. */
    val MAINTENANCE_AIM_DEVIATION: Long get() = getWeight("MAINTENANCE_AIM_DEVIATION", 2_300L)
    /** Consolidation: penalize each active slot so ties collapse to fewer, higher-set lifts (G5). */
    val SLOT_ACTIVATION_COUNT: Long get() = getWeight("SLOT_ACTIVATION_COUNT", 1_000L)
    /**
     * Lower-body anchor activation bonus (Bug #2 fix): a NEGATIVE weight (reward) for activating
     * SquatPattern/HipHinge anchor slots. Net cost of activating a lower anchor is
     * [SLOT_ACTIVATION_COUNT] + [LOWER_ANCHOR_ACTIVATION_BONUS] = 1,000 − 100 = +900 (a net penalty
     * at these tuned weights — the optimizer found that the session-fill gate alone is sufficient to
     * ensure lower-body compounds appear, so the anchor bonus is heavily demoted from its prior -250).
     * The solver can still skip a lower anchor when recovery spacing genuinely can't accommodate it.
     */
    val LOWER_ANCHOR_ACTIVATION_BONUS: Long get() = getWeight("LOWER_ANCHOR_ACTIVATION_BONUS", -100L)
    /** Push/pull balance breaks ties consolidation cares about (G10). Below [ANTAGONIST_PAIR_CLOSENESS]. */
    val PUSH_PULL_IMBALANCE: Long get() = getWeight("PUSH_PULL_IMBALANCE", 200L)
    /**
     * Session fill. NOTE (audit §2.2): at 5,000/set this EXCEEDS the effective maintenance-aim cost
     * (2,000/credit-scaled), so when a session is underfilled the solver pulls maintenance muscles UP
     * from PV toward their maintenance hard ceiling to fill it. This is the intended "push toward PV but not
     * RC" behavior — the maintenance ceiling (`WeeklyVolumeTarget.max` for maintenance) is enforced as a hard
     * constraint by `addWeeklyVolumeCeilings`, so volume never exceeds the maintenance max. The original G4 comment ("volume
     * targets win over filling") described the opposite and was wrong.
     */
    val SESSION_FILL_SHORTFALL: Long get() = getWeight("SESSION_FILL_SHORTFALL", 5_000L)
    /** Session set balance sits below weekly min (G4). */
    val SESSION_SET_IMBALANCE: Long get() = getWeight("SESSION_SET_IMBALANCE", 3_500L)
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
    val MAINTENANCE_POSTERIOR_DIRECT_DISCOURAGE: Long get() = getWeight("MAINTENANCE_POSTERIOR_DIRECT_DISCOURAGE", 5_500L)
    /** G14: Reduced aim-deviation weight for maintenance PosteriorDeltoid — the PV aim is partially
     *  met by row secondaries, so the solver should tolerate a shortfall from direct work
     *  rather than burning session time that bis/tris/lateral should win. Sits above
     *  [SLOT_ACTIVATION_COUNT] but below [MAINTENANCE_AIM_DEVIATION] and far below
     *  [MAINTENANCE_POSTERIOR_DIRECT_DISCOURAGE] so direct work is always a net cost. */
    val MAINTENANCE_POSTERIOR_AIM_DEVIATION: Long get() = getWeight("MAINTENANCE_POSTERIOR_AIM_DEVIATION", 500L)
}

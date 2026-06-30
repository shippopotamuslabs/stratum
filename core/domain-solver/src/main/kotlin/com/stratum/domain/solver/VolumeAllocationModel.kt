@file:Suppress("MagicNumber", "MaxLineLength", "ComplexCondition", "LoopWithTooManyJumpStatements")

package com.stratum.domain.solver

import com.google.ortools.sat.CpModel
import com.google.ortools.sat.IntVar
import com.google.ortools.sat.LinearExpr
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.prescription.FilledSlot
import com.stratum.domain.program.generation.prescription.SessionSlotPlan
import com.stratum.domain.program.generation.reference.PATTERN_SECONDARY_MUSCLE_CREDITS
import com.stratum.domain.program.generation.reference.SESSION_MUSCLE_SET_CAP
import com.stratum.domain.program.generation.reference.MIN_SPECIALIZED_DIRECT_SETS
import com.stratum.domain.program.generation.reference.sessionHardSetCapForLength
import com.stratum.domain.program.generation.split.SlotRole
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets

/**
 * Builds the CP-SAT model for volume allocation. One [IntVar] per included
 * [FilledSlot] (decision variable `x[s]`), linear credit expressions per muscle
 * (weekly + per-session), hard constraints mirroring the greedy allocator's
 * caps and minimums, and a deficit-weighted deviation objective.
 *
 * All credits and targets are scaled by [VolumeAllocationCoefficients.SCALE]
 * (2×) so the 0.5 secondary-credit increments become integer coefficients.
 */
internal class VolumeAllocationModel(
    private val weeklyTargets: WeeklyVolumeTargets,
    private val context: ProgramGenerationContext,
) {
    /** Per-slot cap: 4 for compounds (Anchor/SecondaryCompound), 6 otherwise. */
    private fun perSlotCap(role: SlotRole): Int =
        if (role == SlotRole.Anchor || role == SlotRole.SecondaryCompound) COMPOUND_SETS_PER_SLOT_CAP else SPECIALIZED_SETS_PER_SLOT_CAP

    /** Slot decision variables and their owning plan, in the order added. */
    data class SlotVar(val plan: SessionSlotPlan, val slot: FilledSlot, val x: IntVar)

    val slots: List<SlotVar> get() = _slots
    private val _slots = mutableListOf<SlotVar>()

    /** Muscles that receive any credit from the included slots. */
    val muscles: List<String> get() = _muscles
    private val _muscles = mutableListOf<String>()

    /**
     * Builds decision variables, hard constraints (upper bounds only — always
     * feasible), and the objective (deficit-weighted fill + role-minimum and
     * MEV-floor soft penalties) from the given [plans] (whose `filledSlots`
     * are already at `setCount = 0`). Returns the model ready to solve.
     *
     * **Hard constraints** (always satisfiable — set all x[s] = 0):
     * per-slot upper cap, per-session muscle credit ≤ cap, session time cap,
     * MRV ceiling.
     *
     * **Soft penalties** (in the objective): deviation from per-muscle aim,
     * co-specialization balance, MEV floor shortfall, role-minimum shortfall.
     * The solver gets as close to these as the hard constraints allow.
     */
    fun buildModel(plans: List<SessionSlotPlan>): CpModel {
        val model = CpModel()

        // Decision variables x[s] ∈ [0, perSlotCap].
        plans.forEach { plan ->
            plan.filledSlots.forEach { slot ->
                val x = model.newIntVar(0L, perSlotCap(slot.role).toLong(), "x_${slot.slotId}")
                _slots.add(SlotVar(plan, slot, x))
            }
        }

        // Union of muscles: those with a weekly target plus those credited by any slot.
        val muscleSet = linkedSetOf<String>()
        muscleSet.addAll(weeklyTargets.keys)
        _slots.forEach { sv -> muscleSet.add(sv.slot.primaryMuscle) }
        _slots.forEach { sv ->
            PATTERN_SECONDARY_MUSCLE_CREDITS[sv.slot.pattern]?.forEach { muscleSet.add(it.muscle) }
        }
        _muscles.addAll(muscleSet)

        // Hard constraints (upper bounds only — always feasible).
        addPerSessionMuscleCaps(model)
        addSessionTimeCaps(model)
        addMrvCeilings(model)
        // Objective (soft penalties for aims, MEV floors, role minimums, balance).
        buildObjective(model)
        return model
    }

    /** `credit_session(w, m) ≤ SESSION_MUSCLE_SET_CAP` (scaled). */
    private fun addPerSessionMuscleCaps(model: CpModel) {
        val capScaled = SESSION_MUSCLE_SET_CAP * VolumeAllocationCoefficients.SCALE
        val byPlan = _slots.groupBy { it.plan }
        byPlan.forEach { (_, slotVars) ->
            _muscles.forEach { muscle ->
                val terms = slotVars.mapNotNull { sv ->
                    val c = VolumeAllocationCoefficients.scaledCoeff(sv.slot, muscle)
                    if (c == 0L) null else sv.x to c
                }
                if (terms.isEmpty()) return@forEach
                val expr = LinearExpr.weightedSum(
                    terms.map { it.first }.toTypedArray(),
                    terms.map { it.second }.toLongArray(),
                )
                model.addLessOrEqual(expr, capScaled)
            }
        }
    }

    /** `Σ x[s] ≤ sessionHardSetCap` per session when the cap is non-null. */
    private fun addSessionTimeCaps(model: CpModel) {
        val cap = sessionHardSetCapForLength(context.sessionLengthMinutes) ?: return
        val byPlan = _slots.groupBy { it.plan }
        byPlan.forEach { (_, slotVars) ->
            val expr = LinearExpr.sum(slotVars.map { it.x }.toTypedArray())
            model.addLessOrEqual(expr, cap.toLong())
        }
    }

    /** `credit(m) ≤ target.max` (MRV ceiling — hard upper bound). */
    private fun addMrvCeilings(model: CpModel) {
        _muscles.forEach { muscle ->
            val target = weeklyTargets[muscle] ?: return@forEach
            val weekly = weeklyCreditExpr(muscle) ?: return@forEach
            model.addLessOrEqual(weekly, (target.max * VolumeAllocationCoefficients.SCALE).toLong())
        }
    }

    /**
     * Objective: deficit-weighted fill + MEV-floor shortfall + role-minimum
     * shortfall + co-specialization balance. All terms are soft penalties —
     * the model is always feasible (x[s] = 0 for all slots satisfies every
     * hard upper-bound constraint). The solver gets as close to aims, MEV
     * floors, and role minimums as the caps allow.
     *
     * **Per-muscle deviation:** `credit(m) - aim(m) = over_m - under_m`,
     * penalized at SPECIALIZE_WEIGHT (10) or MAINTENANCE_WEIGHT (3).
     *
     * **MEV floor shortfall:** `credit(m) - min(m) = mevOver - mevUnder`,
     * penalized at MEV_FLOOR_WEIGHT (8) — only the shortfall (mevUnder) is
     * penalized, so surplus above MEV is free.
     *
     * **Role-minimum shortfall:** `x[s] - min(s) = roleOver - roleUnder`,
     * penalized at ROLE_MIN_WEIGHT (6) — only the shortfall (roleUnder).
     */
    private fun buildObjective(model: CpModel) {
        val weightedTerms = mutableListOf<Pair<IntVar, Long>>()

        _muscles.forEach { muscle ->
            val target = weeklyTargets[muscle] ?: return@forEach
            val weekly = weeklyCreditExpr(muscle) ?: return@forEach
            // Aim deviation (over + under).
            val aimScaled = (target.aim * VolumeAllocationCoefficients.SCALE).toLong()
            val over = model.newIntVar(0L, MAX_OVER, "over_$muscle")
            val under = model.newIntVar(0L, MAX_OVER, "under_$muscle")
            model.addEquality(
                LinearExpr.weightedSum(arrayOf(weekly, over, under), longArrayOf(1L, -1L, 1L)),
                aimScaled,
            )
            val weight = if ((context.specialization[muscle] ?: "Maintenance") == "Specialize") {
                SPECIALIZE_WEIGHT
            } else {
                MAINTENANCE_WEIGHT
            }
            weightedTerms.add(over to weight)
            weightedTerms.add(under to weight)

            // MEV floor shortfall (only under is penalized).
            if (target.min > 0.0) {
                val minScaled = (target.min * VolumeAllocationCoefficients.SCALE).toLong()
                val mevUnder = model.newIntVar(0L, MAX_OVER, "mevUnder_$muscle")
                model.addGreaterOrEqual(
                    LinearExpr.weightedSum(arrayOf(weekly, mevUnder), longArrayOf(1L, 1L)),
                    minScaled,
                )
                weightedTerms.add(mevUnder to MEV_FLOOR_WEIGHT)
            }
        }

        // Role-minimum shortfalls (only under is penalized).
        _slots.forEach { sv ->
            val minSets = roleMinimum(sv.slot) ?: return@forEach
            val roleUnder = model.newIntVar(0L, MAX_OVER, "roleUnder_${sv.slot.slotId}")
            model.addGreaterOrEqual(
                LinearExpr.weightedSum(arrayOf(sv.x, roleUnder), longArrayOf(1L, 1L)),
                minSets.toLong(),
            )
            weightedTerms.add(roleUnder to ROLE_MIN_WEIGHT)
        }

        addCoSpecializationBalance(model, weightedTerms)
        val objective = LinearExpr.weightedSum(
            weightedTerms.map { it.first }.toTypedArray(),
            weightedTerms.map { it.second }.toLongArray(),
        )
        model.minimize(objective)
    }

    /** Role minimum for a slot: 3 (anchor), 2 (secondary compound), 4 (specialized isolation), else null. */
    private fun roleMinimum(slot: FilledSlot): Int? = when (slot.role) {
        SlotRole.Anchor -> ANCHOR_MIN_SETS
        SlotRole.SecondaryCompound -> SECONDARY_COMPOUND_MIN_SETS
        SlotRole.Isolation ->
            if ((context.specialization[slot.primaryMuscle] ?: "Maintenance") == "Specialize") {
                MIN_SPECIALIZED_DIRECT_SETS
            } else {
                null
            }
        SlotRole.Optional -> null
    }

    /**
     * Penalizes the absolute weekly-credit difference between every pair of
     * co-specialized muscles that share the same `aim`. This is the global
     * generalization of `DualArmSpecBalancer`: when two muscles are both
     * Specialized and target the same weekly volume (e.g. bicep/tricep with
     * identical landmarks), the solver should not fill one to MRV while
     * leaving the other short. Pairs with *different* aims (e.g. Quad/Glute)
     * are left to the per-muscle deviation objective — forcing them toward a
     * shared midpoint would under-serve the higher-aim muscle.
     *
     * Encoded as `|credit_a - credit_b| = diffAbs`,
     * `diffAbs = diffOver - diffUnder` with both slacks ≥ 0, penalized at
     * [CO_SPECIALIZATION_BALANCE_WEIGHT].
     */
    private fun addCoSpecializationBalance(
        model: CpModel,
        weightedTerms: MutableList<Pair<IntVar, Long>>,
    ) {
        val specializedMuscles = _muscles.filter {
            (context.specialization[it] ?: "Maintenance") == "Specialize"
        }
        if (specializedMuscles.size < 2) return
        for (i in specializedMuscles.indices) {
            for (j in (i + 1) until specializedMuscles.size) {
                val aimA = weeklyTargets[specializedMuscles[i]]?.aim ?: continue
                val aimB = weeklyTargets[specializedMuscles[j]]?.aim ?: continue
                // Only balance pairs with (near-)identical aims.
                if (kotlin.math.abs(aimA - aimB) > AIM_EQUALITY_TOLERANCE) continue
                val creditA = weeklyCreditExpr(specializedMuscles[i]) ?: continue
                val creditB = weeklyCreditExpr(specializedMuscles[j]) ?: continue
                val diffOver = model.newIntVar(0L, MAX_OVER, "diffOver_${specializedMuscles[i]}_${specializedMuscles[j]}")
                val diffUnder = model.newIntVar(0L, MAX_OVER, "diffUnder_${specializedMuscles[i]}_${specializedMuscles[j]}")
                // creditA - creditB = diffOver - diffUnder
                model.addEquality(
                    LinearExpr.weightedSum(
                        arrayOf(creditA, creditB, diffOver, diffUnder),
                        longArrayOf(1L, -1L, -1L, 1L),
                    ),
                    0L,
                )
                weightedTerms.add(diffOver to CO_SPECIALIZATION_BALANCE_WEIGHT)
                weightedTerms.add(diffUnder to CO_SPECIALIZATION_BALANCE_WEIGHT)
            }
        }
    }

    /** Weekly credit expression for [muscle], or null if no slot credits it. */
    private fun weeklyCreditExpr(muscle: String): LinearExpr? {
        val terms = _slots.mapNotNull { sv ->
            val c = VolumeAllocationCoefficients.scaledCoeff(sv.slot, muscle)
            if (c == 0L) null else sv.x to c
        }
        if (terms.isEmpty()) return null
        return LinearExpr.weightedSum(
            terms.map { it.first }.toTypedArray(),
            terms.map { it.second }.toLongArray(),
        )
    }

    private companion object {
        const val COMPOUND_SETS_PER_SLOT_CAP = 4
        const val SPECIALIZED_SETS_PER_SLOT_CAP = 6
        const val ANCHOR_MIN_SETS = 3
        const val SECONDARY_COMPOUND_MIN_SETS = 2
        // Objective weights (long, scaled). Specialize prioritized over Maintenance.
        const val SPECIALIZE_WEIGHT = 10L
        const val MAINTENANCE_WEIGHT = 3L
        // MEV floor shortfall penalty — high enough to push the solver toward
        // meeting MEV when caps allow, but soft so short sessions don't go infeasible.
        const val MEV_FLOOR_WEIGHT = 8L
        // Role-minimum shortfall penalty — pushes anchor/compound/spec-isolation
        // slots toward their floors. Soft so tight session budgets don't break feasibility.
        const val ROLE_MIN_WEIGHT = 6L
        // Penalty for weekly-credit imbalance between co-specialized muscles
        // with (near-)identical aims. Must exceed SPECIALIZE_WEIGHT so the
        // solver prefers balancing co-specialized muscles over hitting an
        // individual aim exactly — secondary-credit asymmetry (e.g. tricep
        // gets credit from 3 press patterns vs bicep's 2 pull patterns)
        // would otherwise force a 1-set imbalance that the deviation term
        // alone won't correct.
        const val CO_SPECIALIZATION_BALANCE_WEIGHT = 15L
        // Aims within this tolerance are treated as equal for balance purposes.
        const val AIM_EQUALITY_TOLERANCE = 0.5
        // Upper bound on over/under slack: ample headroom for any realistic weekly set count.
        const val MAX_OVER = 1000L
    }
}

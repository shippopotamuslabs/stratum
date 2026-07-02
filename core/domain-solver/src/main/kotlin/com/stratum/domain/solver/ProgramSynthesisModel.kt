@file:Suppress("MagicNumber", "MaxLineLength", "ComplexCondition", "LoopWithTooManyJumpStatements", "LargeClass", "TooManyFunctions")

package com.stratum.domain.solver

import com.google.ortools.sat.CpModel
import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.IntVar
import com.google.ortools.sat.LinearExpr
import com.google.ortools.sat.Literal
import com.google.ortools.util.Domain
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.context.isPullPattern
import com.stratum.domain.program.generation.context.isPushPattern
import com.stratum.domain.program.generation.prescription.FilledSlot
import com.stratum.domain.program.generation.prescription.SessionSlotPlan
import com.stratum.domain.program.generation.prescription.SynthesisObjectiveBreakdown
import com.stratum.domain.program.generation.reference.LiftStressClass
import com.stratum.domain.program.generation.reference.MUSCLE_RECOVERY_LOW_DOSE_MAX
import com.stratum.domain.program.generation.reference.MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE
import com.stratum.domain.program.generation.reference.MIN_SPECIALIZED_DIRECT_SETS
import com.stratum.domain.program.generation.reference.MIN_TOUCH_DIRECT_SETS
import com.stratum.domain.program.generation.reference.MUSCLE_RECOVERY_MODERATE_DOSE
import com.stratum.domain.program.generation.reference.MUSCLE_RECOVERY_MODERATE_DOSE_MAX
import com.stratum.domain.program.generation.reference.MuscleSessionStress
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.reference.computePatternSecondaryCredits
import com.stratum.domain.program.generation.reference.SESSION_MUSCLE_SET_CAP
import com.stratum.domain.program.generation.reference.SETS_PER_LIFT_CAP
import com.stratum.domain.program.generation.reference.calendarDayPairsWithMaxGap
import com.stratum.domain.program.generation.reference.calendarGapBetweenDays
import com.stratum.domain.program.generation.reference.isLargeCompoundMuscle
import com.stratum.domain.program.generation.reference.minimumRecoveryCalendarGap
import com.stratum.domain.program.generation.reference.sessionHardSetCapForLength
import com.stratum.domain.program.generation.split.SlotRole
import com.stratum.domain.program.generation.split.calendarAdjacentTrainingDayPairs
import com.stratum.domain.program.generation.split.trainingWeekdayIndices
import com.stratum.domain.program.generation.synthesis.SynthesisSessionDomain
import com.stratum.domain.program.generation.synthesis.SynthesisSlot
import com.stratum.domain.program.generation.synthesis.UnifiedSynthesisSolution
import com.stratum.domain.program.generation.synthesis.parseSessionSlotId
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets

/**
 * Joint CP-SAT model: per-slot set counts, session-to-weekday assignment, and a
 * tiered least-bad objective covering recovery, specialization, and session quality.
 */
internal class ProgramSynthesisModel(
    private val weeklyTargets: WeeklyVolumeTargets,
    private val context: ProgramGenerationContext,
    private val unifiedUniverse: List<SynthesisSlot>,
    private val coefficients: VolumeAllocationCoefficients =
        VolumeAllocationCoefficients(computePatternSecondaryCredits(context)),
) {
    data class SlotVar(
        val plan: SessionSlotPlan,
        val slot: FilledSlot,
        val x: IntVar,
        val active: Literal? = null,
    )

    val slots: List<SlotVar> get() = _slots
    private val _slots = mutableListOf<SlotVar>()

    val muscles: List<String> get() = _muscles
    private val _muscles = mutableListOf<String>()

    val dayVars: List<IntVar> get() = _dayVars
    private val _dayVars = mutableListOf<IntVar>()
    internal val dayVarByPosition: Map<Int, IntVar> get() = _dayVarByPosition
    private val _dayVarByPosition = mutableMapOf<Int, IntVar>()

    private val objectiveSlacks = mutableMapOf<String, IntVar>()
    private val sessionMuscleCredits = mutableMapOf<Int, MutableMap<String, IntVar>>()
    private val meaningfulExposureBySessionMuscle = mutableMapOf<Pair<Int, String>, Literal>()
    private val heavyAxialExposureBySessionMuscle = mutableMapOf<Pair<Int, String>, Literal>()
    private val upperCompoundExposureBySessionMuscle = mutableMapOf<Pair<Int, String>, Literal>()
    private val isolationExposureBySessionMuscle = mutableMapOf<Pair<Int, String>, Literal>()
    private val lowDoseTierBySessionMuscle = mutableMapOf<Pair<Int, String>, Literal>()
    private val moderateDoseTierBySessionMuscle = mutableMapOf<Pair<Int, String>, Literal>()
    private val highDoseBySessionMuscle = mutableMapOf<Pair<Int, String>, Literal>()
    private val requiresTwoDayRecoveryBySessionMuscle = mutableMapOf<Pair<Int, String>, Literal>()
    private val unifiedStructureState = UnifiedStructureState()
    /** Cached `(session position, weekday) → isOnDay` literals, shared across all adjacency loops. */
    private val isOnDayByPosition = mutableMapOf<Pair<Int, Int>, Literal>()
    /** Cached `((posI, posJ), (d1, d2)) → both-on-days` literals, shared across recovery/forbid loops. */
    private val bothOnDaysByKey = mutableMapOf<Pair<Pair<Int, Int>, Pair<Int, Int>>, Literal>()

    fun buildModel(plans: List<SessionSlotPlan>): CpModel {
        val model = CpModel()
        sessionMuscleCredits.clear()
        isOnDayByPosition.clear()
        bothOnDaysByKey.clear()
        moderateDoseBySessionMuscle.clear()
        highDoseBySessionMuscle.clear()
        requiresTwoDayRecoveryBySessionMuscle.clear()
        heavyAxialExposureBySessionMuscle.clear()
        upperCompoundExposureBySessionMuscle.clear()
        isolationExposureBySessionMuscle.clear()
        lowDoseTierBySessionMuscle.clear()
        moderateDoseTierBySessionMuscle.clear()
        meaningfulExposureBySessionMuscle.clear()
        _slots.clear()
        _muscles.clear()
        _dayVars.clear()
        _dayVarByPosition.clear()
        objectiveSlacks.clear()
        meaningfulExposureBySessionMuscle.clear()
        heavyAxialPrimaryExposureBySlot.clear()
        heavyAxialPrimaryHardForbidBySlot.clear()
        unifiedStructureState.domainVarByPosition.clear()
        plans.forEach { plan ->
            plan.filledSlots.forEach { slot ->
                val cap = perSlotCap(slot)
                val x = model.newIntVar(0L, cap.toLong(), "x_${slot.slotId}")
                // Unified mode: every slot is solver-activatable (gated by the universe). The legacy
                // non-unified path (per-slot isSolverActivatableSlot) is gone — CpSatProgramSynthesizer
                // always passes a universe.
                val active = model.newBoolVar("active_${slot.slotId}")
                val slotVar = SlotVar(plan, slot, x, active)
                if (active != null) {
                    model.addEquality(x, 0).onlyEnforceIf(active.not())
                }
                _slots.add(slotVar)
            }
        }

        val muscleSet = linkedSetOf<String>()
        muscleSet.addAll(weeklyTargets.keys)
        _slots.forEach { sv -> muscleSet.add(sv.slot.primaryMuscle) }
        _slots.forEach { sv ->
            coefficients.musclesForPattern(sv.slot.pattern).forEach { muscleSet.add(it.muscle) }
        }
        _muscles.addAll(muscleSet)

        addCalendarAssignment(model, plans)
        addUnifiedStructureConstraints(model, plans, unifiedUniverse, _slots, unifiedStructureState)
        addPerSessionMuscleCaps(model)
        addSessionTimeCaps(model)
        addWeeklyVolumeCeilings(model)
        addLowerLegAnchorFloors(model)
        addMinSetsPerActiveSlot(model)
        addRoleMinimumConstraints(model)
        addMinimumActiveLiftsConstraint(model, plans)
        addAdjacentMeaningfulExposureForbids(model, plans, _muscles.toSet())
        addHeavyAxialSpacingForbids(model, plans)
        buildObjective(model, plans)
        return model
    }

    fun readUnifiedSolution(
        solver: CpSolver,
        plans: List<SessionSlotPlan>,
    ): UnifiedSynthesisSolution {
        val setCounts = mutableMapOf<Pair<Int, String>, Int>()
        val activeBySession = mutableMapOf<Int, MutableSet<String>>()
        for (sv in _slots) {
            val parsed = parseSessionSlotId(sv.slot.slotId) ?: continue
            val (sessionIndex, universeId) = parsed
            val isActive = sv.active == null || solver.booleanValue(sv.active)
            val sets = solver.value(sv.x).toInt()
            if (isActive && sets > 0) {
                setCounts[sessionIndex to universeId] = sets
                activeBySession.getOrPut(sessionIndex) { mutableSetOf() }.add(universeId)
            }
        }
        val weekdayIndexByPosition =
            plans.sortedBy { it.position }.map { plan ->
                solver.value(dayVarByPosition.getValue(plan.position)).toInt()
            }
        val domainBySession =
            plans.sortedBy { it.position }.map { plan ->
                val ordinal =
                    solver.value(unifiedStructureState.domainVarByPosition.getValue(plan.position)).toInt()
                SynthesisSessionDomain.entries[ordinal]
            }
        return UnifiedSynthesisSolution(
            activeSlotIdsBySession =
                plans.sortedBy { it.position }.map { plan ->
                    activeBySession[plan.position].orEmpty()
                },
            setCountBySessionSlot = setCounts,
            weekdayIndexByPosition = weekdayIndexByPosition,
            domainBySession = domainBySession,
        )
    }

    fun readObjective(solver: CpSolver): SynthesisObjectiveBreakdown {
        fun sumSlack(prefix: String): Long =
            objectiveSlacks.filter { (name, _) -> name.startsWith(prefix) }.values.sumOf { solver.value(it) }

        val recovery = sumSlack("rec_pri_") + sumSlack("rec_gen_") + sumSlack("rec_adj_")
        val mev = sumSlack("mevUnder_")
        val coSpec = sumSlack("diffOver_") + sumSlack("diffUnder_")
        val specAim = sumSlack("over_spec_") + sumSlack("under_spec_")
        val maintAim = sumSlack("over_maint_") + sumSlack("under_maint_")
        val minVolAim = sumSlack("over_minvol_") + sumSlack("under_minvol_")
        val role = sumSlack("roleUnder_")
        val pushPull = sumSlack("ppOver_") + sumSlack("ppUnder_")
        val sessionFill = sumSlack("sessUnder_")
        return SynthesisObjectiveBreakdown(
            total = solver.objectiveValue().toLong(),
            recoveryPenalty = recovery,
            specializedMevShortfall = mev,
            coSpecializationImbalance = coSpec,
            specializeAimDeviation = specAim,
            maintenanceAimDeviation = maintAim,
            minimumVolumeAimDeviation = minVolAim,
            roleMinimumShortfall = role,
            pushPullImbalance = pushPull,
            sessionFillShortfall = sessionFill,
        )
    }

    private fun perSlotCap(slot: FilledSlot): Int =
        when {
            slot.role == SlotRole.SecondaryCompound -> COMPOUND_SETS_PER_SLOT_CAP
            slot.role == SlotRole.Anchor && slot.repProfile != RpRepProfile.Isolation ->
                COMPOUND_SETS_PER_SLOT_CAP
            else -> SETS_PER_LIFT_CAP
        }

    private fun addCalendarAssignment(model: CpModel, plans: List<SessionSlotPlan>) {
        val rawWeekdayCount = context.weekdays.distinct().size
        require(context.weekdays.size == rawWeekdayCount) {
            "Training weekdays contain duplicates: ${context.weekdays}. Each day must be distinct."
        }
        val trainingDays = trainingWeekdayIndices(context.weekdays)
        require(trainingDays.size == plans.size) {
            "Training day count ${trainingDays.size} must match session count ${plans.size} " +
                "(weekdays=${context.weekdays})."
        }
        plans.sortedBy { it.position }.forEach { plan ->
            val day =
                model.newIntVarFromDomain(
                    Domain.fromValues(trainingDays.map { it.toLong() }.toLongArray()),
                    "day_${plan.position}",
                )
            _dayVars.add(day)
            _dayVarByPosition[plan.position] = day
        }
        model.addAllDifferent(_dayVars.toTypedArray())
        // Symmetry breaking: in unified mode every session is structurally identical (same slot
        // universe; only the assigned weekday differs). Without this, every solution has up to P!
        // symmetric day-permutations, so the solver wastes the deadline exploring copies.
        // Constraining day vars to strictly increase by session position canonicalizes the
        // assignment; downstream re-sorts sessions by weekday anyway, so output is stable.
        for (i in 1 until _dayVars.size) {
            model.addLessThan(_dayVars[i - 1], _dayVars[i])
        }
    }

    private fun addPerSessionMuscleCaps(model: CpModel) {
        val capScaled = SESSION_MUSCLE_SET_CAP * coefficients.scale
        val byPlan = _slots.groupBy { it.plan }
        byPlan.forEach { (_, slotVars) ->
            _muscles.forEach { muscle ->
                val terms =
                    slotVars.mapNotNull { sv ->
                        val c = coefficients.scaledCoeff(sv.slot, muscle)
                        if (c == 0L) null else sv.x to c
                    }
                if (terms.isEmpty()) return@forEach
                val expr =
                    LinearExpr.weightedSum(
                        terms.map { it.first }.toTypedArray(),
                        terms.map { it.second }.toLongArray(),
                    )
                model.addLessOrEqual(expr, capScaled)
            }
        }
    }

    private fun addSessionTimeCaps(model: CpModel) {
        val cap = sessionHardSetCapForLength(context.sessionLengthMinutes) ?: return
        val byPlan = _slots.groupBy { it.plan }
        byPlan.forEach { (_, slotVars) ->
            val expr = LinearExpr.sum(slotVars.map { it.x }.toTypedArray())
            model.addLessOrEqual(expr, cap.toLong())
        }
    }

    private fun addWeeklyVolumeCeilings(model: CpModel) {
        _muscles.forEach { muscle ->
            val weekly = weeklyCreditExpr(muscle) ?: return@forEach
            val level = context.specialization[muscle] ?: "Maintenance"
            if (level == "Omit") {
                // Omitted muscles are excluded from weeklyTargets, so without this they'd have no
                // ceiling at all — yet a required anchor (e.g. squat on a lower session) still gives
                // them volume. Cap incidental omitted-muscle volume at a conservative maintenance-MV
                // band so it can't run away (audit §7.3). Uses the landmark MV when available.
                val snapshot = context.muscleVolumeSnapshotsByMuscle[muscle]
                val mvCeiling = snapshot?.retentionFloorMv ?: OMITTED_MUSCLE_INCIDENTAL_CEILING
                model.addLessOrEqual(weekly, (mvCeiling * coefficients.scale).toLong())
                return@forEach
            }
            val target = weeklyTargets[muscle] ?: return@forEach
            model.addLessOrEqual(weekly, (target.max * coefficients.scale).toLong())
        }
    }

    /** Squat/hinge anchors must stay at programming minimum on every session that includes them. */
    private fun addLowerLegAnchorFloors(model: CpModel) {
        val sessionCap = sessionHardSetCapForLength(context.sessionLengthMinutes) ?: return
        if (sessionCap < ANCHOR_MIN_SETS * 2) return
        _slots.forEach { sv ->
            if (sv.slot.role != SlotRole.Anchor) return@forEach
            if (sv.slot.pattern !in LOWER_LEG_ANCHOR_PATTERNS) return@forEach
            val floor =
                model.addGreaterOrEqual(sv.x, ANCHOR_MIN_SETS.toLong())
            sv.active.let { active -> floor.onlyEnforceIf(active) }
        }
    }

    private fun buildObjective(model: CpModel, plans: List<SessionSlotPlan>) {
        val weightedTerms = mutableListOf<Pair<IntVar, Long>>()
        val priorityMuscles = recoveryPriorityMuscles(context)

        addRecoveryPenalties(model, plans, priorityMuscles, weightedTerms)
        addPushPullBalance(model, weightedTerms)
        addSessionFillPenalties(model, plans, weightedTerms)
        addSessionSetBalancePenalties(model, plans, weightedTerms)

        _muscles.forEach { muscle ->
            val target = weeklyTargets[muscle] ?: return@forEach
            val weekly = weeklyCreditExpr(muscle) ?: return@forEach
            val level = context.specialization[muscle] ?: "Maintenance"
            val aimScaled = (target.aim * coefficients.scale).toLong()
            val prefix =
                when (level) {
                    "Specialize" -> "spec"
                    "MinimumVolume" -> "minvol"
                    else -> "maint"
                }
            val over = trackSlack(model, "over_${prefix}_$muscle", CREDIT_DEVIATION_SLACK_MAX)
            val under = trackSlack(model, "under_${prefix}_$muscle", CREDIT_DEVIATION_SLACK_MAX)
            model.addEquality(
                LinearExpr.weightedSum(arrayOf(weekly, over, under), longArrayOf(1L, -1L, 1L)),
                aimScaled,
            )
            val weight =
                when (level) {
                    "Specialize" -> SynthesisObjectiveWeights.SPECIALIZE_AIM_DEVIATION
                    "MinimumVolume" -> SynthesisObjectiveWeights.MINIMUM_VOLUME_AIM_DEVIATION
                    // G14: PosteriorDeltoid gets partial coverage from HorizontalPull secondaries
                    // (0.5/set), so the PV aim is often unreachable from secondaries alone and
                    // direct work competes with bicep/tricep/lateral for session time. A lower
                    // aim-deviation weight lets the solver tolerate a posterior shortfall rather
                    // than burning sets on direct rear-delt work that bis/tris/lateral should win.
                    else -> if (muscle == "PosteriorDeltoid") {
                        SynthesisObjectiveWeights.MAINTENANCE_POSTERIOR_AIM_DEVIATION
                    } else {
                        SynthesisObjectiveWeights.MAINTENANCE_AIM_DEVIATION
                    }
                }
            weightedTerms.add(over to weight)
            weightedTerms.add(under to weight)

            if (target.min > 0.0 && level == "Specialize") {
                val minScaled = (target.min * coefficients.scale).toLong()
                val minUnder = trackSlack(model, "minUnder_$muscle", CREDIT_DEVIATION_SLACK_MAX)
                model.addGreaterOrEqual(
                    LinearExpr.weightedSum(arrayOf(weekly, minUnder), longArrayOf(1L, 1L)),
                    minScaled,
                )
                weightedTerms.add(minUnder to SynthesisObjectiveWeights.SPECIALIZED_MIN_SHORTFALL)
            }
        }

        // Consolidation (G5, spec §6 "slot activation count"): penalize each active slot so
        // equal-credit optima collapse to fewer, higher-set lifts (the "2–5 sets per lift"
        // shape) rather than fragmented many-slots × 1-set solutions. Modest weight, above
        // aim-noise but below weekly min, so it only breaks ties without under-shooting volume.
        _slots.forEach { sv ->
            val active = sv.active as? com.google.ortools.sat.BoolVar ?: return@forEach
            // Per-raw-set weight: scale by the credit scale so it stays proportional to the
            // credit-denominated aim/min weights (scale-invariance across SCALE changes).
            weightedTerms.add(active to SynthesisObjectiveWeights.SLOT_ACTIVATION_COUNT * coefficients.scale)
        }

        // Lower-body anchor activation bonus: reward activating SquatPattern/HipHinge anchors so
        // the solver prefers to fire them rather than leaving the compound slot inactive and
        // letting the cheap `quad-iso`/`ham-iso` isolation slots carry all the muscle's volume
        // (the Bug #2 symptom: Leg Extension as the sole quad work). The bonus outweighs the
        // SLOT_ACTIVATION_COUNT penalty (net −150 × scale per lower anchor) so the solver actively
        // wants them active, but stays far below recovery/aim weights so it won't override volume
        // targets or force infeasible recovery spacing. Soft, not hard — the solver can still
        // skip a lower anchor when recovery spacing genuinely can't accommodate it.
        _slots.forEach { sv ->
            if (sv.slot.role != SlotRole.Anchor) return@forEach
            if (sv.slot.pattern !in LOWER_LEG_ANCHOR_PATTERNS) return@forEach
            val active = sv.active as? com.google.ortools.sat.BoolVar ?: return@forEach
            weightedTerms.add(active to SynthesisObjectiveWeights.LOWER_ANCHOR_ACTIVATION_BONUS * coefficients.scale)
        }

        addMaintenanceRearDeltDirectDiscouragement(model, weightedTerms)
        addAntagonistMaintenanceBalance(model, weightedTerms)
        addSymmetricMaintenanceBalance(model, weightedTerms)
        addCoSpecializationBalance(model, weightedTerms)
        val objective =
            LinearExpr.weightedSum(
                weightedTerms.map { it.first }.toTypedArray(),
                weightedTerms.map { it.second }.toLongArray(),
            )
        model.minimize(objective)
    }

    private fun addRecoveryPenalties(
        model: CpModel,
        plans: List<SessionSlotPlan>,
        priorityMuscles: Set<String>,
        weightedTerms: MutableList<Pair<IntVar, Long>>,
    ) {
        val trainingDays = trainingWeekdayIndices(context.weekdays)

        for (muscle in _muscles) {
            val sessionsWithMuscle =
                plans.filter { plan -> maxSessionCreditScaled(plan, muscle) > 0L }
            if (sessionsWithMuscle.size < 2) continue
            val weight =
                if (muscle in priorityMuscles) {
                    SynthesisObjectiveWeights.RECOVERY_PRIORITY
                } else {
                    SynthesisObjectiveWeights.RECOVERY_GENERAL
                }

            for (i in sessionsWithMuscle.indices) {
                for (j in (i + 1) until sessionsWithMuscle.size) {
                    addRecoveryPenaltiesForSessionPair(
                        model = model,
                        planI = sessionsWithMuscle[i],
                        planJ = sessionsWithMuscle[j],
                        muscle = muscle,
                        weight = weight,
                        trainingDays = trainingDays,
                        weightedTerms = weightedTerms,
                    )
                }
            }
        }
    }

    private fun addRecoveryPenaltiesForSessionPair(
        model: CpModel,
        planI: SessionSlotPlan,
        planJ: SessionSlotPlan,
        muscle: String,
        weight: Long,
        trainingDays: List<Int>,
        weightedTerms: MutableList<Pair<IntVar, Long>>,
    ) {
        // Iterate every ordered (earlier, later) day pair — including non-adjacent ones (gap ≥ 2) —
        // so the 48–72h tier in `minimumRecoveryCalendarGap` is actually enforceable. Previously only
        // gap-1 pairs were generated, so Mon→Wed (gap 2, required 3 for a large compound at moderate
        // dose) was never penalized while the Sat→Mon wrap (also gap 2) was — an asymmetry.
        // Each ordered pair is added once; channelDayPair covers both sessions' day assignments.
        val sortedDays = trainingDays.sorted()
        for (earlier in sortedDays.indices) {
            for (later in sortedDays.indices) {
                if (earlier == later) continue
                val earlierDay = sortedDays[earlier]
                val laterDay = sortedDays[later]
                val calendarGap = if (laterDay > earlierDay) laterDay - earlierDay else 7 - earlierDay + laterDay
                addGapPenalty(
                    model = model,
                    planI = planI,
                    planJ = planJ,
                    muscle = muscle,
                    weight = weight,
                    weightedTerms = weightedTerms,
                    earlierDay = earlierDay,
                    laterDay = laterDay,
                    calendarGap = calendarGap,
                    sessionPosI = planI.position,
                    sessionPosJ = planJ.position,
                )
            }
        }
    }

    /**
     * Hard forbid calendar-adjacent (gap-1) meaningful repeats when the **earlier** session's
     * resolved (stress class × dose tier) requires ≥ 48h ([minimumRecoveryCalendarGap] ≥ 2).
     *
     * Isolation low-dose (≈2–3 credits, 1–3 working sets) stays on the 24h lane — adjacent
     * meaningful exposure is allowed. Moderate-or-higher isolation, all upper-compound, and all
     * heavy-axial meaningful loads still hard-forbid gap-1 repeats. Longer gaps remain soft in
     * [addRecoveryPenalties]; gap-2 heavy-axial primary spacing is in [addHeavyAxialSpacingForbids].
     */
    private fun addAdjacentMeaningfulExposureForbids(
        model: CpModel,
        plans: List<SessionSlotPlan>,
        muscles: Set<String>,
    ) {
        if (muscles.isEmpty()) return
        val trainingDays = trainingWeekdayIndices(context.weekdays).sorted()
        val adjacentPairs = calendarAdjacentTrainingDayPairs(trainingDays)
        for (muscle in muscles) {
            val sessions = plans.filter { plan -> maxSessionCreditScaled(plan, muscle) > 0L }
            for (i in sessions.indices) {
                for (j in (i + 1) until sessions.size) {
                    val planI = sessions[i]
                    val planJ = sessions[j]
                    val creditI = sessionMuscleCreditVar(model, planI, muscle) ?: continue
                    val creditJ = sessionMuscleCreditVar(model, planJ, muscle) ?: continue
                    val meaningfulI = meaningfulExposureLiteral(model, creditI, muscle, planI.position)
                    val meaningfulJ = meaningfulExposureLiteral(model, creditJ, muscle, planJ.position)
                    val requiresTwoDayI = requiresTwoDayRecoveryLiteral(model, planI, muscle, creditI)
                    val requiresTwoDayJ = requiresTwoDayRecoveryLiteral(model, planJ, muscle, creditJ)
                    for ((d1, d2) in adjacentPairs) {
                        val earlierIsI = channelDayPair(model, planI.position, planJ.position, d1, d2)
                        model.addBoolOr(
                            arrayOf(
                                earlierIsI.not(),
                                meaningfulI.not(),
                                meaningfulJ.not(),
                                requiresTwoDayI.not(),
                            ),
                        )
                        val earlierIsJ = channelDayPair(model, planJ.position, planI.position, d1, d2)
                        model.addBoolOr(
                            arrayOf(
                                earlierIsJ.not(),
                                meaningfulI.not(),
                                meaningfulJ.not(),
                                requiresTwoDayJ.not(),
                            ),
                        )
                    }
                }
            }
        }
    }

    /**
     * Hard forbid the 48h repeat (gap-2 calendar days, e.g. Mon→Wed) when the EARLIER session
     * carries moderate-or-higher [LiftStressClass.HeavyAxial] load on a slot's **primary mover**
     * (free-weight squat/hinge). Secondary credits from the same barbell (e.g. Glute from squats)
     * follow standard 48h recovery via [addAdjacentMeaningfulExposureForbids] and soft penalties only.
     *
     * Hard forbid gates on primary set count ≥ [MUSCLE_RECOVERY_MODERATE_DOSE] (8 sets ≈ 8.0 credits).
     * One-way lookback (matches [addAdjacentMeaningfulExposureForbids]): the earlier session's
     * moderate heavy-axial primary dose drives the 72h recovery debt.
     */
    private fun addHeavyAxialSpacingForbids(
        model: CpModel,
        plans: List<SessionSlotPlan>,
    ) {
        val trainingDays = trainingWeekdayIndices(context.weekdays).sorted()
        val gapTwoPairs =
            calendarDayPairsWithMaxGap(trainingDays, maxGap = 2)
                .filter { (earlier, later) -> calendarGapBetweenDays(earlier, later) == 2 }
        if (gapTwoPairs.isEmpty()) return
        val heavyAxialSlots =
            _slots.filter { sv ->
                coefficients.isHeavyAxialSlot(sv.slot) && sv.active != null
            }
        if (heavyAxialSlots.isEmpty()) return
        for (sv in heavyAxialSlots) {
            val planEarlier = sv.plan
            val heavyAxialPrimary = heavyAxialPrimaryExposureSlotLiteral(model, sv)
            val hardForbidPrimary = heavyAxialPrimaryHardForbidSlotLiteral(model, sv)
            for (planLater in plans) {
                if (planLater.position == planEarlier.position) continue
                for ((d1, d2) in gapTwoPairs) {
                    val earlierIsEarlier = channelDayPair(
                        model,
                        planEarlier.position,
                        planLater.position,
                        d1,
                        d2,
                    )
                    model.addBoolOr(
                        arrayOf(earlierIsEarlier.not(), hardForbidPrimary.not(), heavyAxialPrimary.not()),
                    )
                }
            }
        }
    }

    private val heavyAxialPrimaryExposureBySlot = mutableMapOf<String, Literal>()
    private val heavyAxialPrimaryHardForbidBySlot = mutableMapOf<String, Literal>()

    /** True iff this heavy-axial slot is active with ≥ 1 primary set (meaningful primary-mover load). */
    private fun heavyAxialPrimaryExposureSlotLiteral(model: CpModel, sv: SlotVar): Literal =
        heavyAxialPrimaryExposureBySlot.getOrPut(sv.slot.slotId) {
            val active = sv.active ?: error("heavy-axial slot ${sv.slot.slotId} must be activatable")
            val hasMinSets = model.newBoolVar("haPriMin_${sv.slot.slotId}")
            model.addGreaterOrEqual(sv.x, 1L).onlyEnforceIf(hasMinSets)
            model.addLessOrEqual(sv.x, 0L).onlyEnforceIf(hasMinSets.not())
            val exposed = reifyAndLiteral(model, active, hasMinSets, "haPriExp_${sv.slot.slotId}")
            exposed
        }

    /** True iff this heavy-axial slot is active at moderate+ primary sets (≥ 8). */
    private fun heavyAxialPrimaryHardForbidSlotLiteral(model: CpModel, sv: SlotVar): Literal =
        heavyAxialPrimaryHardForbidBySlot.getOrPut(sv.slot.slotId) {
            val active = sv.active ?: error("heavy-axial slot ${sv.slot.slotId} must be activatable")
            val hardScaled = (MUSCLE_RECOVERY_MODERATE_DOSE * coefficients.scale).toLong()
            val hardDose = model.newBoolVar("haPriHardDose_${sv.slot.slotId}")
            model.addGreaterOrEqual(sv.x, hardScaled).onlyEnforceIf(hardDose)
            model.addLessOrEqual(sv.x, hardScaled - 1).onlyEnforceIf(hardDose.not())
            reifyAndLiteral(model, active, hardDose, "haPriHard_${sv.slot.slotId}")
        }

    private fun sessionMuscleCreditVar(
        model: CpModel,
        plan: SessionSlotPlan,
        muscle: String,
    ): IntVar? {
        val byMuscle = sessionMuscleCredits.getOrPut(plan.position) { mutableMapOf() }
        byMuscle[muscle]?.let { return it }
        val terms =
            _slots.filter { it.plan == plan }.mapNotNull { sv ->
                val coeff = coefficients.scaledCoeff(sv.slot, muscle)
                if (coeff == 0L) null else sv.x to coeff
            }
        if (terms.isEmpty()) return null
        val maxCredit = maxSessionCreditScaled(plan, muscle)
        val credit = model.newIntVar(0L, maxCredit, "credit_${plan.position}_$muscle")
        model.addEquality(
            credit,
            LinearExpr.weightedSum(
                terms.map { it.first }.toTypedArray(),
                terms.map { it.second }.toLongArray(),
            ),
        )
        byMuscle[muscle] = credit
        return credit
    }

    /**
     * Literal true iff the session's TOTAL credit for [muscle] (primary + secondary) reaches the
     * meaningful threshold. Secondary load counts toward the muscle's fatigue — a triceps set is a
     * triceps set whether it's a direct extension or a bench-press secondary — so the gate uses total
     * credit, not direct-only. Threshold 2.0 (≈2 hard sets) is the low end of the 24–48h recovery
     * band (Sousa et al. 2024): below this the exposure is incidental and may repeat the next day.
     */
    private fun meaningfulExposureLiteral(
        model: CpModel,
        credit: IntVar,
        muscle: String,
        sessionPosition: Int,
    ): Literal {
        meaningfulExposureBySessionMuscle[sessionPosition to muscle]?.let { return it }
        val minScaled = (MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE * coefficients.scale).toLong()
        val meaningful = model.newBoolVar("meaningful_${muscle}_${sessionPosition}")
        model.addGreaterOrEqual(credit, minScaled).onlyEnforceIf(meaningful)
        model.addLessOrEqual(credit, minScaled - 1).onlyEnforceIf(meaningful.not())
        meaningfulExposureBySessionMuscle[sessionPosition to muscle] = meaningful
        return meaningful
    }

    private val moderateDoseBySessionMuscle = mutableMapOf<Pair<Int, String>, Literal>()

    /** Literal true iff the session's credit for [muscle] reaches the moderate-dose tier
     *  (≥ [SOLVER_MODERATE_DOSE_THRESHOLD] fractional credits ≈ 4–7 hard sets). Used for
     *  class-aware soft penalties. */
    private fun moderateDoseLiteral(
        model: CpModel,
        credit: IntVar,
        muscle: String,
        sessionPosition: Int,
    ): Literal =
        moderateDoseBySessionMuscle.getOrPut(sessionPosition to muscle) {
            val moderateScaled = SOLVER_MODERATE_DOSE_THRESHOLD_SCALED
            val moderate = model.newBoolVar("moderate_${muscle}_${sessionPosition}")
            model.addGreaterOrEqual(credit, moderateScaled).onlyEnforceIf(moderate)
            model.addLessOrEqual(credit, moderateScaled - 1).onlyEnforceIf(moderate.not())
            moderate
        }

    /** Literal true iff credit exceeds the moderate tier (high-volume band, > 7.0 credits). */
    private fun highDoseLiteral(
        model: CpModel,
        credit: IntVar,
        muscle: String,
        sessionPosition: Int,
    ): Literal =
        highDoseBySessionMuscle.getOrPut(sessionPosition to muscle) {
            val highScaled = (MUSCLE_RECOVERY_MODERATE_DOSE_MAX * coefficients.scale).toLong()
            val high = model.newBoolVar("high_${muscle}_${sessionPosition}")
            model.addGreaterOrEqual(credit, highScaled + 1).onlyEnforceIf(high)
            model.addLessOrEqual(credit, highScaled).onlyEnforceIf(high.not())
            high
        }

    /**
     * Literal true iff any heavy-axial slot credits ≥ 1.0 fractional sets to [muscle] as its
     * **primary mover** in this session. Secondary credits (e.g. Glute from squats) follow
     * isolation recovery via [isolationExposureLiteral].
     */
    private fun heavyAxialExposureLiteral(
        model: CpModel,
        plan: SessionSlotPlan,
        muscle: String,
    ): Literal =
        heavyAxialExposureBySessionMuscle.getOrPut(plan.position to muscle) {
            val slotContributions =
                _slots.filter {
                    it.plan == plan &&
                        coefficients.isHeavyAxialSlot(it.slot) &&
                        it.slot.primaryMuscle == muscle
                }
                    .mapNotNull { sv ->
                        slotContributesOneCreditLiteral(model, sv, muscle) ?: return@mapNotNull null
                    }
            if (slotContributions.isEmpty()) {
                val absent = model.newBoolVar("heavyAxial_${muscle}_${plan.position}")
                model.addEquality(absent, 0)
                absent
            } else {
                reifyOrLiteral(model, slotContributions, "heavyAxial_${muscle}_${plan.position}")
            }
        }

    /**
     * Literal true iff any upper-compound slot credits ≥ 1.0 fractional sets to [muscle] as its
     * **primary mover** and no heavy-axial primary slot does (dominant-class rule: HeavyAxial
     * wins over UpperCompound). Secondary credits (e.g. Bicep from rows) follow isolation recovery.
     */
    private fun upperCompoundExposureLiteral(
        model: CpModel,
        plan: SessionSlotPlan,
        muscle: String,
    ): Literal =
        upperCompoundExposureBySessionMuscle.getOrPut(plan.position to muscle) {
            val slotContributions =
                _slots.filter {
                    it.plan == plan &&
                        coefficients.isUpperCompoundSlot(it.slot) &&
                        it.slot.primaryMuscle == muscle
                }
                    .mapNotNull { sv ->
                        slotContributesOneCreditLiteral(model, sv, muscle) ?: return@mapNotNull null
                    }
            if (slotContributions.isEmpty()) {
                val absent = model.newBoolVar("upperCompound_${muscle}_${plan.position}")
                model.addEquality(absent, 0)
                absent
            } else {
                val hasUpper = reifyOrLiteral(
                    model,
                    slotContributions,
                    "upperCompoundRaw_${muscle}_${plan.position}",
                )
                val heavyAxial = heavyAxialExposureLiteral(model, plan, muscle)
                reifyAndLiteral(
                    model,
                    hasUpper,
                    heavyAxial.not(),
                    "upperCompound_${muscle}_${plan.position}",
                )
            }
        }

    /** Literal true iff the session's dominant stress class for [muscle] is isolation. */
    private fun isolationExposureLiteral(
        model: CpModel,
        plan: SessionSlotPlan,
        muscle: String,
    ): Literal =
        isolationExposureBySessionMuscle.getOrPut(plan.position to muscle) {
            val heavyAxial = heavyAxialExposureLiteral(model, plan, muscle)
            val upperCompound = upperCompoundExposureLiteral(model, plan, muscle)
            reifyAndLiteral(
                model,
                heavyAxial.not(),
                upperCompound.not(),
                "isolation_${muscle}_${plan.position}",
            )
        }

    /** Literal true iff [sv] credits ≥ 1.0 fractional sets to [muscle] when active. */
    private fun slotContributesOneCreditLiteral(
        model: CpModel,
        sv: SlotVar,
        muscle: String,
    ): Literal? {
        val coeff = coefficients.scaledCoeff(sv.slot, muscle)
        if (coeff == 0L) return null
        val oneCreditScaled = coefficients.scale
        val maxSlotCredit = perSlotCap(sv.slot).toLong() * coeff
        val slotCredit = model.newIntVar(0L, maxSlotCredit, "slotCredit_${sv.slot.slotId}_$muscle")
        model.addEquality(
            slotCredit,
            LinearExpr.term(sv.x, coeff),
        )
        val contributes = model.newBoolVar("slotContrib_${sv.slot.slotId}_$muscle")
        model.addGreaterOrEqual(slotCredit, oneCreditScaled).onlyEnforceIf(contributes)
        model.addLessOrEqual(slotCredit, oneCreditScaled - 1).onlyEnforceIf(contributes.not())
        sv.active?.let { active ->
            model.addImplication(active.not(), contributes.not())
        }
        return contributes
    }

    private fun reifyOrLiteral(
        model: CpModel,
        terms: List<Literal>,
        name: String,
    ): Literal {
        if (terms.size == 1) return terms.single()
        val result = model.newBoolVar(name)
        model.addBoolOr(terms.toTypedArray()).onlyEnforceIf(result)
        val notTerms = terms.map { it.not() }.toTypedArray()
        model.addBoolAnd(notTerms).onlyEnforceIf(result.not())
        return result
    }

    private fun lowDoseTierLiteral(
        model: CpModel,
        credit: IntVar,
        muscle: String,
        sessionPosition: Int,
    ): Literal =
        lowDoseTierBySessionMuscle.getOrPut(sessionPosition to muscle) {
            val meaningful = meaningfulExposureLiteral(model, credit, muscle, sessionPosition)
            val moderate = moderateDoseLiteral(model, credit, muscle, sessionPosition)
            reifyAndLiteral(model, meaningful, moderate.not(), "lowDose_${muscle}_${sessionPosition}")
        }

    private fun moderateDoseTierLiteral(
        model: CpModel,
        credit: IntVar,
        muscle: String,
        sessionPosition: Int,
    ): Literal =
        moderateDoseTierBySessionMuscle.getOrPut(sessionPosition to muscle) {
            val moderate = moderateDoseLiteral(model, credit, muscle, sessionPosition)
            val high = highDoseLiteral(model, credit, muscle, sessionPosition)
            reifyAndLiteral(model, moderate, high.not(), "moderateTier_${muscle}_${sessionPosition}")
        }

    /**
     * True when this session's resolved (class × dose tier) for [muscle] needs ≥ 48h before repeat
     * (calendar gap ≥ 2). Isolation low-dose stays false so the 24h adjacent lane remains open.
     */
    private fun requiresTwoDayRecoveryLiteral(
        model: CpModel,
        plan: SessionSlotPlan,
        muscle: String,
        credit: IntVar,
    ): Literal =
        requiresTwoDayRecoveryBySessionMuscle.getOrPut(plan.position to muscle) {
            val isolationClass = isolationExposureLiteral(model, plan, muscle)
            val upperCompoundClass = upperCompoundExposureLiteral(model, plan, muscle)
            val heavyAxialClass = heavyAxialExposureLiteral(model, plan, muscle)
            val lowTier = lowDoseTierLiteral(model, credit, muscle, plan.position)
            val moderateTier = moderateDoseTierLiteral(model, credit, muscle, plan.position)
            val highTier = highDoseLiteral(model, credit, muscle, plan.position)
            val gates =
                buildList {
                    // Muscle-mass penalty: Isolation Low on a large compound muscle (Quad/Hamstring/
                    // Glute/Chest/Back) recovers in 36h → calendar gap 2 → adjacent-day forbid.
                    // Small-muscle isolation Low (bicep curl, calf raise) stays at 24h / gap 1.
                    if (isLargeCompoundMuscle(muscle)) {
                        add(reifyAndLiteral(model, lowTier, isolationClass, "adj2d_isoLo_${muscle}_${plan.position}"))
                    }
                    add(reifyAndLiteral(model, moderateTier, isolationClass, "adj2d_isoMod_${muscle}_${plan.position}"))
                    add(reifyAndLiteral(model, highTier, isolationClass, "adj2d_isoHi_${muscle}_${plan.position}"))
                    add(reifyAndLiteral(model, lowTier, upperCompoundClass, "adj2d_ucLo_${muscle}_${plan.position}"))
                    add(reifyAndLiteral(model, moderateTier, upperCompoundClass, "adj2d_ucMod_${muscle}_${plan.position}"))
                    add(reifyAndLiteral(model, highTier, upperCompoundClass, "adj2d_ucHi_${muscle}_${plan.position}"))
                    add(reifyAndLiteral(model, lowTier, heavyAxialClass, "adj2d_haLo_${muscle}_${plan.position}"))
                    add(reifyAndLiteral(model, moderateTier, heavyAxialClass, "adj2d_haMod_${muscle}_${plan.position}"))
                    add(reifyAndLiteral(model, highTier, heavyAxialClass, "adj2d_haHi_${muscle}_${plan.position}"))
                }
            if (gates.isEmpty()) {
                val absent = model.newBoolVar("reqTwoDay_${muscle}_${plan.position}")
                model.addEquality(absent, 0)
                absent
            } else {
                reifyOrLiteral(model, gates, "reqTwoDay_${muscle}_${plan.position}")
            }
        }

    /** Reify `result <=> left AND right` (CP-SAT half-reified encoding). */
    private fun reifyAndLiteral(
        model: CpModel,
        left: Literal,
        right: Literal,
        name: String,
    ): Literal {
        val result = model.newBoolVar(name)
        model.addBoolOr(arrayOf(left.not(), right.not(), result))
        model.addBoolOr(arrayOf(left, right, result.not()))
        return result
    }

    private fun addGapPenalty(
        model: CpModel,
        planI: SessionSlotPlan,
        planJ: SessionSlotPlan,
        muscle: String,
        weight: Long,
        weightedTerms: MutableList<Pair<IntVar, Long>>,
        earlierDay: Int,
        laterDay: Int,
        calendarGap: Int,
        sessionPosI: Int,
        sessionPosJ: Int,
    ) {
        val creditI = sessionMuscleCreditVar(model, planI, muscle) ?: return
        val creditJ = sessionMuscleCreditVar(model, planJ, muscle) ?: return
        val meaningfulI = meaningfulExposureLiteral(model, creditI, muscle, planI.position)
        val meaningfulJ = meaningfulExposureLiteral(model, creditJ, muscle, planJ.position)
        val both = channelDayPair(model, sessionPosI, sessionPosJ, earlierDay, laterDay)
        val calendarAdjacent = calendarGap == 1
        val penaltyScale = if (calendarAdjacent) CALENDAR_ADJACENT_PENALTY_MULTIPLIER else 1
        val prefix = if (weight == SynthesisObjectiveWeights.RECOVERY_PRIORITY) "rec_pri_" else "rec_gen_"

        // planI is always on the calendar-earlier day (symmetry breaking on day vars). Price each
        // (dose tier × stress class) combination using tier literals on the solved credit var.
        val isolationClass = isolationExposureLiteral(model, planI, muscle)
        val upperCompoundClass = upperCompoundExposureLiteral(model, planI, muscle)
        val heavyAxialClass = heavyAxialExposureLiteral(model, planI, muscle)
        val lowTier = lowDoseTierLiteral(model, creditI, muscle, planI.position)
        val moderateTier = moderateDoseTierLiteral(model, creditI, muscle, planI.position)
        val highTier = highDoseLiteral(model, creditI, muscle, planI.position)

        val penaltySpecs =
            listOf(
                RecoveryPenaltySpec(LiftStressClass.Isolation, LOW_TIER_REPRESENTATIVE_DOSE, lowTier, isolationClass),
                RecoveryPenaltySpec(LiftStressClass.Isolation, MODERATE_TIER_REPRESENTATIVE_DOSE, moderateTier, isolationClass),
                RecoveryPenaltySpec(LiftStressClass.Isolation, HIGH_TIER_REPRESENTATIVE_DOSE, highTier, isolationClass),
                RecoveryPenaltySpec(LiftStressClass.UpperCompound, LOW_TIER_REPRESENTATIVE_DOSE, lowTier, upperCompoundClass),
                RecoveryPenaltySpec(LiftStressClass.UpperCompound, MODERATE_TIER_REPRESENTATIVE_DOSE, moderateTier, upperCompoundClass),
                RecoveryPenaltySpec(LiftStressClass.UpperCompound, HIGH_TIER_REPRESENTATIVE_DOSE, highTier, upperCompoundClass),
                RecoveryPenaltySpec(LiftStressClass.HeavyAxial, LOW_TIER_REPRESENTATIVE_DOSE, lowTier, heavyAxialClass),
                RecoveryPenaltySpec(LiftStressClass.HeavyAxial, MODERATE_TIER_REPRESENTATIVE_DOSE, moderateTier, heavyAxialClass),
                RecoveryPenaltySpec(LiftStressClass.HeavyAxial, HIGH_TIER_REPRESENTATIVE_DOSE, highTier, heavyAxialClass),
            )

        for ((index, spec) in penaltySpecs.withIndex()) {
            val required =
                minimumRecoveryCalendarGap(
                    MuscleSessionStress(
                        fractionalSets = spec.representativeDose,
                        stressClass = spec.stressClass,
                        // Muscle-aware soft penalty: applies the +12h Low-tier surcharge for
                        // isolation lifts on large compound muscles (Quad/Hamstring/Glute/Chest/Back)
                        // so e.g. Leg Extension at 2 sets prices at 36h, not 24h. The lengthened-state
                        // surcharge is not priced here because this layer prices representative
                        // dose-tier × stress-class combos without lift metadata; the hard-forbid and
                        // post-solver repair paths carry the length bias directly.
                        muscle = muscle,
                    ),
                )
            if (required <= 0 || calendarGap >= required) continue
            val shortfall = required - calendarGap
            val maxPenalty =
                (shortfall * shortfall * RECOVERY_GAP_PENALTY_BASE * penaltyScale)
                    .toLong()
                    .coerceAtMost(RECOVERY_SLACK_CAP)
            val slack =
                trackSlack(
                    model,
                    "${prefix}${muscle}_${earlierDay}_${laterDay}_${calendarGap}_${sessionPosI}_${sessionPosJ}_$index",
                    RECOVERY_SLACK_CAP,
                )
            val penalty = model.addGreaterOrEqual(slack, maxPenalty)
            penalty.onlyEnforceIf(both)
            penalty.onlyEnforceIf(meaningfulI)
            penalty.onlyEnforceIf(meaningfulJ)
            penalty.onlyEnforceIf(spec.tierGate)
            penalty.onlyEnforceIf(spec.classGate)
            weightedTerms.add(slack to weight)
        }
    }

    private data class RecoveryPenaltySpec(
        val stressClass: LiftStressClass,
        val representativeDose: Double,
        val tierGate: Literal,
        val classGate: Literal,
    )

    /**
     * Literal that is true iff session [posI] is on weekday [d1] AND session [posJ] is on weekday [d2].
     * The per-(position, day) `isOnDay` literals and the per-day-pair `both` literal are memoized, so
     * the recovery/forbid loops — which call this across muscles × session-pairs × day-pairs — reuse
     * the same reifications instead of minting hundreds of redundant boolean triples.
     */
    private fun channelDayPair(
        model: CpModel,
        posI: Int,
        posJ: Int,
        d1: Int,
        d2: Int,
    ): Literal {
        return bothOnDaysByKey.getOrPut((posI to posJ) to (d1 to d2)) {
            val onI = isOnDayLiteral(model, posI, d1)
            val onJ = isOnDayLiteral(model, posJ, d2)
            val both = model.newBoolVar("both_${posI}_${posJ}_${d1}_${d2}")
            model.addBoolOr(arrayOf(onI.not(), onJ.not(), both))
            model.addBoolOr(arrayOf(onI, onJ, both.not()))
            both
        }
    }

    private fun isOnDayLiteral(model: CpModel, position: Int, day: Int): Literal =
        isOnDayByPosition.getOrPut(position to day) {
            val dayVar = _dayVarByPosition.getValue(position)
            val onDay = model.newBoolVar("onDay_${position}_$day")
            model.addEquality(dayVar, day.toLong()).onlyEnforceIf(onDay)
            model.addDifferent(dayVar, day.toLong()).onlyEnforceIf(onDay.not())
            onDay
        }

    private fun addPushPullBalance(model: CpModel, weightedTerms: MutableList<Pair<IntVar, Long>>) {
        val pushTerms = _slots.filter { sv -> isPushPattern(sv.slot.pattern) }
        val pullTerms = _slots.filter { sv -> isPullPattern(sv.slot.pattern) }
        if (pushTerms.isEmpty() || pullTerms.isEmpty()) return
        val push = LinearExpr.sum(pushTerms.map { it.x }.toTypedArray())
        val pull = LinearExpr.sum(pullTerms.map { it.x }.toTypedArray())
        val over = trackSlack(model, "ppOver")
        val under = trackSlack(model, "ppUnder")
        model.addEquality(
            LinearExpr.weightedSum(arrayOf(push, pull, over, under), longArrayOf(1L, -1L, -1L, 1L)),
            0L,
        )
        // Push/pull slacks are in raw sets; scale the weight to match credit-denominated tiers.
        val ppWeight = SynthesisObjectiveWeights.PUSH_PULL_IMBALANCE * coefficients.scale
        weightedTerms.add(over to ppWeight)
        weightedTerms.add(under to ppWeight)
    }

    private fun addSessionFillPenalties(
        model: CpModel,
        plans: List<SessionSlotPlan>,
        weightedTerms: MutableList<Pair<IntVar, Long>>,
    ) {
        val sessionCap = sessionHardSetCapForLength(context.sessionLengthMinutes)?.toLong() ?: return
        // Session budget is fully defined by sessionLengthMinutes / MINUTES_PER_HARD_SET (hard cap above).
        // Soft fill penalizes under-using that budget — no secondary ratio trim.
        val targetSets = sessionCap
        val byPlan = _slots.groupBy { it.plan }
        plans.forEach { plan ->
            val slotVars = byPlan[plan].orEmpty()
            if (slotVars.isEmpty()) return@forEach
            val total = LinearExpr.sum(slotVars.map { it.x }.toTypedArray())
            val under = trackSlack(model, "sessUnder_${plan.position}")
            model.addGreaterOrEqual(
                LinearExpr.weightedSum(arrayOf(total, under), longArrayOf(1L, 1L)),
                targetSets,
            )
            weightedTerms.add(under to SynthesisObjectiveWeights.SESSION_FILL_SHORTFALL * coefficients.scale)
        }
    }

    /** Each session must carry at least an equal share of weekly hard sets (minus slack). */
    private fun addSessionSetBalancePenalties(
        model: CpModel,
        plans: List<SessionSlotPlan>,
        weightedTerms: MutableList<Pair<IntVar, Long>>,
    ) {
        if (plans.size < 2) return
        val sessionTotals =
            plans.mapNotNull { plan ->
                val slotVars = _slots.filter { it.plan == plan }
                if (slotVars.isEmpty()) return@mapNotNull null
                plan.position to LinearExpr.sum(slotVars.map { it.x }.toTypedArray())
            }
        if (sessionTotals.size < plans.size) return
        val n = plans.size.toLong()
        val weeklySum = LinearExpr.sum(sessionTotals.map { it.second }.toTypedArray())
        sessionTotals.forEach { (position, total) ->
            val under = trackSlack(model, "sessBalUnder_$position")
            model.addGreaterOrEqual(
                LinearExpr.weightedSum(
                    arrayOf(total, weeklySum, under),
                    longArrayOf(n, -1L, n),
                ),
                0L,
            )
            weightedTerms.add(under to SynthesisObjectiveWeights.SESSION_SET_IMBALANCE * coefficients.scale)
        }
    }

    /**
     * Hard floor (G2): every active slot carries at least [MIN_SETS_PER_ACTIVE_SLOT] sets.
     * This makes every active exposure recovery-visible (≥ MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE)
     * and eliminates the 1-set lifts the solver produced when nothing told it not to. Gated on
     * the same session-cap threshold as [addMinimumActiveLiftsConstraint] to avoid infeasibility
     * when `MIN_ACTIVE(3) × 2 = 6` would exceed a short-session cap.
     */
    private fun addMinSetsPerActiveSlot(model: CpModel) {
        val sessionCap = sessionHardSetCapForLength(context.sessionLengthMinutes) ?: return
        if (sessionCap < 12) return
        _slots.forEach { sv ->
            val active = sv.active ?: return@forEach
            model.addGreaterOrEqual(sv.x, MIN_SETS_PER_ACTIVE_SLOT.toLong()).onlyEnforceIf(active)
        }
    }

    /**
     * Hard role/specialization minimums (G1, spec §5.4): anchors ≥ [ANCHOR_MIN_SETS],
     * secondary compounds ≥ [SECONDARY_COMPOUND_MIN_SETS], specialized isolations ≥
     * [MIN_SPECIALIZED_DIRECT_SETS], cross-domain touch ≥ [MIN_TOUCH_DIRECT_SETS].
     * Previously soft at weight 50 (trivially cheap), so the solver parked a specialized
     * Calf isolation at 1 set in the audit repro.
     */
    private fun addRoleMinimumConstraints(model: CpModel) {
        _slots.forEach { sv ->
            val minSets = roleMinimum(sv.slot) ?: return@forEach
            val floor = model.addGreaterOrEqual(sv.x, minSets.toLong())
            sv.active?.let { active -> floor.onlyEnforceIf(active) }
        }
    }

    private fun addMinimumActiveLiftsConstraint(model: CpModel, plans: List<SessionSlotPlan>) {
        val sessionCap = sessionHardSetCapForLength(context.sessionLengthMinutes) ?: return
        if (sessionCap < 12) return
        val byPlan = _slots.groupBy { it.plan }
        plans.forEach { plan ->
            val actives = byPlan[plan]?.mapNotNull { it.active }.orEmpty()
            if (actives.size < MIN_ACTIVE_LIFTS_PER_SESSION) return@forEach
            model.addGreaterOrEqual(
                LinearExpr.sum(actives.toTypedArray()),
                MIN_ACTIVE_LIFTS_PER_SESSION.toLong(),
            )
        }
    }

    private fun specializationTierValue(level: String): Int =
        when (level) {
            "Specialize" -> 2
            "Maintenance" -> 1
            "MinimumVolume" -> 0
            else -> 1
        }

    private fun addAntagonistMaintenanceBalance(
        model: CpModel,
        weightedTerms: MutableList<Pair<IntVar, Long>>,
    ) {
        for (pair in ANTAGONIST_MAINTENANCE_PAIRS) {
            if (!shouldBalanceAntagonistPair(pair.weaker, pair.stronger)) continue
            val weakerCredit = weeklyCreditExpr(pair.weaker) ?: continue
            val strongerCredit = weeklyCreditExpr(pair.stronger) ?: continue

            val levelWeaker = context.specialization[pair.weaker] ?: "Maintenance"
            val levelStronger = context.specialization[pair.stronger] ?: "Maintenance"
            val valWeaker = specializationTierValue(levelWeaker)
            val valStronger = specializationTierValue(levelStronger)

            val (wrongWeight, rightWeight) = when {
                valWeaker == valStronger -> {
                    val wrong = SynthesisObjectiveWeights.ANTAGONIST_WRONG_WAY_EXCESS +
                        SynthesisObjectiveWeights.ANTAGONIST_PAIR_CLOSENESS
                    val right = SynthesisObjectiveWeights.ANTAGONIST_RIGHT_WAY_EXCESS +
                        SynthesisObjectiveWeights.ANTAGONIST_PAIR_CLOSENESS
                    wrong to right
                }
                valStronger > valWeaker -> {
                    val wrong = SynthesisObjectiveWeights.ANTAGONIST_WRONG_WAY_EXCESS +
                        SynthesisObjectiveWeights.ANTAGONIST_PAIR_CLOSENESS
                    wrong to 0L
                }
                else -> {
                    0L to 0L
                }
            }

            if (wrongWeight > 0L || rightWeight > 0L) {
                val wrongWayExcess = trackSlack(model, "antWrong_${pair.label}", CREDIT_DEVIATION_SLACK_MAX)
                val rightWayExcess = trackSlack(model, "antRight_${pair.label}", CREDIT_DEVIATION_SLACK_MAX)
                model.addEquality(
                    LinearExpr.weightedSum(
                        arrayOf(weakerCredit, strongerCredit, wrongWayExcess, rightWayExcess),
                        longArrayOf(1L, -1L, -1L, 1L),
                    ),
                    0L,
                )
                if (wrongWeight > 0L) {
                    weightedTerms.add(wrongWayExcess to wrongWeight)
                }
                if (rightWeight > 0L) {
                    weightedTerms.add(rightWayExcess to rightWeight)
                }
            }
        }
    }

    /**
     * Balance antagonist pairs unless either side is Omit. Previously gated on `levelA == levelB`,
     * which skipped the most common real-world config (Chest Maintenance + Back Specialize) and
     * let chest run away with no directional pressure (G9). The equality constraint with slacks
     * (see [addAntagonistMaintenanceBalance]) already permits any split, so firing on a tier
     * mismatch applies directional pressure without forcing the Maintenance muscle to match the
     * Specialize muscle's RC.
     */
    private fun shouldBalanceAntagonistPair(muscleA: String, muscleB: String): Boolean {
        val levelA = context.specialization[muscleA] ?: "Maintenance"
        val levelB = context.specialization[muscleB] ?: "Maintenance"
        if (levelA == "Omit" || levelB == "Omit") return false
        return true
    }

    /**
     * Symmetric pair balance (bicep/tricep): pull weekly credits toward parity with no directional
     * preference. Both directions are penalized equally at [SynthesisObjectiveWeights.SYMMETRIC_PAIR_CLOSENESS],
     * matching the antagonist closeness tier so a bicep/tricep gap is weighed comparably to a
     * chest/back gap. Unlike antagonist pairs there is no wrong-way/right-way split — neither side
     * is preferred. Skipped only when either muscle is Omit; specialization tier is irrelevant
     * because there is no preferred direction to gate on.
     */
    private fun addSymmetricMaintenanceBalance(
        model: CpModel,
        weightedTerms: MutableList<Pair<IntVar, Long>>,
    ) {
        for (pair in SYMMETRIC_MAINTENANCE_PAIRS) {
            if (!shouldBalanceSymmetricPair(pair.muscleA, pair.muscleB)) continue
            val creditA = weeklyCreditExpr(pair.muscleA) ?: continue
            val creditB = weeklyCreditExpr(pair.muscleB) ?: continue

            val excessA = trackSlack(model, "symOver_${pair.label}", CREDIT_DEVIATION_SLACK_MAX)
            val excessB = trackSlack(model, "symUnder_${pair.label}", CREDIT_DEVIATION_SLACK_MAX)
            model.addEquality(
                LinearExpr.weightedSum(
                    arrayOf(creditA, creditB, excessA, excessB),
                    longArrayOf(1L, -1L, -1L, 1L),
                ),
                0L,
            )
            weightedTerms.add(excessA to SynthesisObjectiveWeights.SYMMETRIC_PAIR_CLOSENESS)
            weightedTerms.add(excessB to SynthesisObjectiveWeights.SYMMETRIC_PAIR_CLOSENESS)
        }
    }

    /**
     * Symmetric pair balance fires only when both muscles share the same specialization tier. When
     * tiers differ (e.g. Tricep Specialize + Bicep Maintenance) there is no preferred direction to
     * absorb the gap, so balancing would either inflate the maintenance muscle past its ceiling or
     * cap the specialized muscle below its RC aim — both harmful. Same-tier (notably both
     * Maintenance, the common case) is where parity is both achievable and desirable. Omit always
     * skips. Both-Specialize with equal aims is also handled by [addCoSpecializationBalance].
     */
    private fun shouldBalanceSymmetricPair(muscleA: String, muscleB: String): Boolean {
        val levelA = context.specialization[muscleA] ?: "Maintenance"
        val levelB = context.specialization[muscleB] ?: "Maintenance"
        if (levelA == "Omit" || levelB == "Omit") return false
        return specializationTierValue(levelA) == specializationTierValue(levelB)
    }

    private fun addCoSpecializationBalance(
        model: CpModel,
        weightedTerms: MutableList<Pair<IntVar, Long>>,
    ) {
        val specializedMuscles =
            _muscles.filter { muscle -> (context.specialization[muscle] ?: "Maintenance") == "Specialize" }
        if (specializedMuscles.size < 2) return
        for (i in specializedMuscles.indices) {
            for (j in (i + 1) until specializedMuscles.size) {
                val aimA = weeklyTargets[specializedMuscles[i]]?.aim ?: continue
                val aimB = weeklyTargets[specializedMuscles[j]]?.aim ?: continue
                if (kotlin.math.abs(aimA - aimB) > AIM_EQUALITY_TOLERANCE) continue
                val creditA = weeklyCreditExpr(specializedMuscles[i]) ?: continue
                val creditB = weeklyCreditExpr(specializedMuscles[j]) ?: continue
                val diffOver = trackSlack(model, "diffOver_${specializedMuscles[i]}_${specializedMuscles[j]}", CREDIT_DEVIATION_SLACK_MAX)
                val diffUnder = trackSlack(model, "diffUnder_${specializedMuscles[i]}_${specializedMuscles[j]}", CREDIT_DEVIATION_SLACK_MAX)
                model.addEquality(
                    LinearExpr.weightedSum(
                        arrayOf(creditA, creditB, diffOver, diffUnder),
                        longArrayOf(1L, -1L, -1L, 1L),
                    ),
                    0L,
                )
                weightedTerms.add(diffOver to SynthesisObjectiveWeights.CO_SPECIALIZATION_BALANCE)
                weightedTerms.add(diffUnder to SynthesisObjectiveWeights.CO_SPECIALIZATION_BALANCE)
            }
        }
    }

    private fun maxSessionCreditScaled(plan: SessionSlotPlan, muscle: String): Long {
        var total = 0L
        for (sv in _slots.filter { it.plan == plan }) {
            val cap = perSlotCap(sv.slot).toLong()
            val coeff = coefficients.scaledCoeff(sv.slot, muscle)
            total += cap * coeff
        }
        return total
    }

    /**
     * Deprioritize rear-delt direct work when PosteriorDeltoid is at Maintenance (G14): posterior
     * gets partial coverage from HorizontalPull secondaries (0.5/set), so direct rear-delt work
     * competes with bicep/tricep/lateral-delt isolation for limited session time. Adds a per-set
     * penalty on each PosteriorDeltoid slot; weight sits above
     * [SynthesisObjectiveWeights.SLOT_ACTIVATION_COUNT] so the solver prefers filling
     * bicep/tricep/lateral slots first, but below [SynthesisObjectiveWeights.MAINTENANCE_AIM_DEVIATION]
     * so it never fully blocks rear-delt — if secondaries alone can't reach PV, the solver will still
     * activate rear-delt rather than take the larger aim-deviation penalty. Per-set (not per-activation)
     * so each rear-delt set is individually less valuable than a bicep/tricep/lateral set, flipping
     * the solver's marginal-value ordering when posterior's gap is larger than the arms' gap.
     * PosteriorDeltoid Specialize is exempt — direct work is then wanted.
     */
    private fun addMaintenanceRearDeltDirectDiscouragement(
        model: CpModel,
        weightedTerms: MutableList<Pair<IntVar, Long>>,
    ) {
        val posteriorLevel = context.specialization["PosteriorDeltoid"] ?: "Maintenance"
        if (posteriorLevel == "Specialize") return
        _slots.forEach { sv ->
            if (sv.slot.primaryMuscle != "PosteriorDeltoid") return@forEach
            val sets = model.newIntVar(0L, MAX_OVER, "rearDirect_${sv.slot.slotId}")
            model.addEquality(sets, sv.x)
            // Scale the per-set discouragement by the credit scale so it stays proportional to the
            // per-set posterior-under-aim benefit (which is in scaled credits). Without this, raising
            // SCALE flips the rear-delt economics: a direct set saves 5000 fill + (SCALE × 500)
            // posterior-aim but only costs the unscaled 5500, so the solver over-fills rear delts.
            val scaledDiscourage = SynthesisObjectiveWeights.MAINTENANCE_POSTERIOR_DIRECT_DISCOURAGE * coefficients.scale
            weightedTerms.add(sets to scaledDiscourage)
        }
    }

    private fun roleMinimum(slot: FilledSlot): Int? {
        // Anchor / secondary-compound floors apply by role.
        if (slot.role == SlotRole.Anchor) return ANCHOR_MIN_SETS
        if (slot.role == SlotRole.SecondaryCompound) return SECONDARY_COMPOUND_MIN_SETS
        // Specialized isolation floors apply by rep profile, not SlotRole — the universe builder
        // slots some specialized isolations (e.g. Calf, variant arm curls) as SlotRole.Optional,
        // and the audit's [REPRO] showed a specialized Calf parked at 1 set because the old
        // role-only check returned null for Optional. RpRepProfile.Isolation is the reliable
        // signal that this is a true single-joint isolation that should honor the direct floor.
        val isSpecializedPrimary =
            (context.specialization[slot.primaryMuscle] ?: "Maintenance") == "Specialize"
        if (!isSpecializedPrimary) return null
        return when {
            slot.repProfile != RpRepProfile.Isolation -> null
            slot.isCrossDomainTouch -> MIN_TOUCH_DIRECT_SETS
            else -> MIN_SPECIALIZED_DIRECT_SETS
        }
    }

    private fun weeklyCreditExpr(muscle: String): LinearExpr? {
        val terms =
            _slots.mapNotNull { sv ->
                val c = coefficients.scaledCoeff(sv.slot, muscle)
                if (c == 0L) null else sv.x to c
            }
        if (terms.isEmpty()) return null
        return LinearExpr.weightedSum(
            terms.map { it.first }.toTypedArray(),
            terms.map { it.second }.toLongArray(),
        )
    }

    private fun trackSlack(model: CpModel, name: String): IntVar = trackSlack(model, name, MAX_OVER)

    /**
     * Slack in `[0, max]`. Recovery slacks pass [RECOVERY_SLACK_CAP]; weekly credit slacks
     * (aim, weekly min, antagonist, co-spec) pass [CREDIT_DEVIATION_SLACK_MAX] so volume targets stay
     * genuinely soft. Set-based slacks (fill, balance) use [MAX_OVER].
     */
    private fun trackSlack(model: CpModel, name: String, max: Long): IntVar {
        val slack = model.newIntVar(0L, max, name)
        objectiveSlacks[name] = slack
        return slack
    }

    private companion object {
        /** First credit in the moderate tier: strictly above [MUSCLE_RECOVERY_LOW_DOSE_MAX]. */
        val SOLVER_MODERATE_DOSE_THRESHOLD_SCALED: Long get() =
            (MUSCLE_RECOVERY_LOW_DOSE_MAX * VolumeAllocationCoefficients.SCALE).toLong() + 1L
        const val LOW_TIER_REPRESENTATIVE_DOSE = MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE
        const val MODERATE_TIER_REPRESENTATIVE_DOSE = 5.0
        const val HIGH_TIER_REPRESENTATIVE_DOSE = MUSCLE_RECOVERY_MODERATE_DOSE_MAX + 1.0
        const val COMPOUND_SETS_PER_SLOT_CAP = 4
        const val ANCHOR_MIN_SETS = 2
        const val SECONDARY_COMPOUND_MIN_SETS = 2
        const val AIM_EQUALITY_TOLERANCE = 0.5
        /** Raw-set slacks (session fill, push/pull). */
        const val MAX_OVER = 1000L
        /** Weekly credit slacks — must exceed any realistic aim/min deviation (MV/PV/RC targets). */
        const val CREDIT_DEVIATION_SLACK_MAX = 1_000_000L
        const val RECOVERY_SLACK_CAP = 10_000L
        const val RECOVERY_GAP_PENALTY_BASE = 80
        const val CALENDAR_ADJACENT_PENALTY_MULTIPLIER = 10
        const val MIN_SESSION_HARD_SETS = 8
        const val MIN_ACTIVE_LIFTS_PER_SESSION = 3
        /** Conservative weekly ceiling for an Omitted muscle that still receives incidental anchor volume. */
        const val OMITTED_MUSCLE_INCIDENTAL_CEILING = 6.0
        /** Minimum hard sets on any active slot — every active exposure ≥ 2 sets (G2). */
        const val MIN_SETS_PER_ACTIVE_SLOT = 2
        val LOWER_LEG_ANCHOR_PATTERNS = setOf("SquatPattern", "HipHinge")
        val ANTAGONIST_MAINTENANCE_PAIRS =
            listOf(
                AntagonistMaintenancePair("Chest", "Back", "chest_back"),
                AntagonistMaintenancePair("Quad", "Hamstring", "quad_ham"),
            )
        val SYMMETRIC_MAINTENANCE_PAIRS =
            listOf(
                SymmetricMaintenancePair("Bicep", "Tricep", "bicep_tricep"),
            )
    }

    private data class AntagonistMaintenancePair(
        val weaker: String,
        val stronger: String,
        val label: String,
    )

    private data class SymmetricMaintenancePair(
        val muscleA: String,
        val muscleB: String,
        val label: String,
    )
}

internal fun recoveryPriorityMuscles(context: ProgramGenerationContext): Set<String> =
    context.specialization
        .filterValues { level -> level == "Specialize" || level == "MinimumVolume" }
        .keys

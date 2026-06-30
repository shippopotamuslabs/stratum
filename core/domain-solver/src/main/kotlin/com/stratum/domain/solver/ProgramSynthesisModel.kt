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
import com.stratum.domain.program.generation.reference.MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE
import com.stratum.domain.program.generation.reference.MIN_SPECIALIZED_DIRECT_SETS
import com.stratum.domain.program.generation.reference.MIN_TOUCH_DIRECT_SETS
import com.stratum.domain.program.generation.reference.PATTERN_SECONDARY_MUSCLE_CREDITS
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.reference.SESSION_MUSCLE_SET_CAP
import com.stratum.domain.program.generation.reference.SETS_PER_LIFT_CAP
import com.stratum.domain.program.generation.reference.minimumRecoveryCalendarGap
import com.stratum.domain.program.generation.reference.sessionHardSetCapForLength
import com.stratum.domain.program.generation.split.SessionTypeId
import com.stratum.domain.program.generation.split.SlotRole
import com.stratum.domain.program.generation.split.calendarAdjacentTrainingDayPairs
import com.stratum.domain.program.generation.split.trainingWeekdayIndices
import com.stratum.domain.program.generation.split.trainingWeekdayIndicesInUserOrder
import com.stratum.domain.program.generation.prescription.isSolverActivatableSlot
import com.stratum.domain.program.generation.prescription.prefersSlotActivation
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
    private val unifiedUniverse: List<SynthesisSlot>? = null,
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
    private val unifiedStructureState = UnifiedStructureState()

    fun buildModel(plans: List<SessionSlotPlan>): CpModel {
        val model = CpModel()
        sessionMuscleCredits.clear()
        meaningfulExposureBySessionMuscle.clear()
        _slots.clear()
        _muscles.clear()
        _dayVars.clear()
        _dayVarByPosition.clear()
        objectiveSlacks.clear()
        meaningfulExposureBySessionMuscle.clear()
        unifiedStructureState.domainVarByPosition.clear()
        plans.forEach { plan ->
            plan.filledSlots.forEach { slot ->
                val cap = perSlotCap(slot)
                val x = model.newIntVar(0L, cap.toLong(), "x_${slot.slotId}")
                val activatable = unifiedUniverse != null || isSolverActivatableSlot(slot)
                val active =
                    if (activatable) {
                        model.newBoolVar("active_${slot.slotId}")
                    } else {
                        null
                    }
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
            PATTERN_SECONDARY_MUSCLE_CREDITS[sv.slot.pattern]?.forEach { muscleSet.add(it.muscle) }
        }
        _muscles.addAll(muscleSet)

        addCalendarAssignment(model, plans)
        if (unifiedUniverse != null) {
            addUnifiedStructureConstraints(model, plans, unifiedUniverse, _slots, unifiedStructureState)
        }
        addPerSessionMuscleCaps(model)
        addSessionTimeCaps(model)
        addMrvCeilings(model)
        addLowerLegAnchorFloors(model)
        if (unifiedUniverse != null) {
            addMinimumActiveLiftsConstraint(model, plans)
            addConsecutiveDayDomainConstraints(model, plans)
            addPriorityMuscleAdjacentForbids(model, plans, recoveryPriorityMuscles(context))
        }
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
            if (unifiedUniverse != null) {
                plans.sortedBy { it.position }.map { plan ->
                    val ordinal =
                        solver.value(unifiedStructureState.domainVarByPosition.getValue(plan.position)).toInt()
                    SynthesisSessionDomain.entries[ordinal]
                }
            } else {
                emptyList()
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
        val trainingDays = trainingWeekdayIndices(context.weekdays)
        require(trainingDays.size == plans.size) {
            "Training day count ${trainingDays.size} must match session count ${plans.size}"
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
    }

    private fun addPerSessionMuscleCaps(model: CpModel) {
        val capScaled = SESSION_MUSCLE_SET_CAP * VolumeAllocationCoefficients.SCALE
        val byPlan = _slots.groupBy { it.plan }
        byPlan.forEach { (_, slotVars) ->
            _muscles.forEach { muscle ->
                val terms =
                    slotVars.mapNotNull { sv ->
                        val c = VolumeAllocationCoefficients.scaledCoeff(sv.slot, muscle)
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

    private fun addMrvCeilings(model: CpModel) {
        _muscles.forEach { muscle ->
            val target = weeklyTargets[muscle] ?: return@forEach
            val weekly = weeklyCreditExpr(muscle) ?: return@forEach
            model.addLessOrEqual(weekly, (target.max * VolumeAllocationCoefficients.SCALE).toLong())
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
            if (unifiedUniverse != null) {
                sv.active?.let { active -> floor.onlyEnforceIf(active) }
            }
        }
    }

    private fun buildObjective(model: CpModel, plans: List<SessionSlotPlan>) {
        val weightedTerms = mutableListOf<Pair<IntVar, Long>>()
        val priorityMuscles = recoveryPriorityMuscles(context)

        addRecoveryPenalties(model, plans, priorityMuscles, weightedTerms)
        addConsecutiveTrainingDayExposurePenalties(model, plans, priorityMuscles, weightedTerms)
        addSessionTypeAdjacencyPenalties(model, plans, weightedTerms)
        addPushPullBalance(model, weightedTerms)
        addSessionFillPenalties(model, plans, weightedTerms)
        addSessionSetBalancePenalties(model, plans, weightedTerms)

        _muscles.forEach { muscle ->
            val target = weeklyTargets[muscle] ?: return@forEach
            val weekly = weeklyCreditExpr(muscle) ?: return@forEach
            val level = context.specialization[muscle] ?: "Maintenance"
            val aimScaled = (target.aim * VolumeAllocationCoefficients.SCALE).toLong()
            val prefix =
                when (level) {
                    "Specialize" -> "spec"
                    "MinimumVolume" -> "minvol"
                    else -> "maint"
                }
            val over = trackSlack(model, "over_${prefix}_$muscle")
            val under = trackSlack(model, "under_${prefix}_$muscle")
            model.addEquality(
                LinearExpr.weightedSum(arrayOf(weekly, over, under), longArrayOf(1L, -1L, 1L)),
                aimScaled,
            )
            val weight =
                when (level) {
                    "Specialize" -> SynthesisObjectiveWeights.SPECIALIZE_AIM_DEVIATION
                    "MinimumVolume" -> SynthesisObjectiveWeights.MINIMUM_VOLUME_AIM_DEVIATION
                    else -> SynthesisObjectiveWeights.MAINTENANCE_AIM_DEVIATION
                }
            weightedTerms.add(over to weight)
            weightedTerms.add(under to weight)

            if (target.min > 0.0 && level == "Specialize") {
                val minScaled = (target.min * VolumeAllocationCoefficients.SCALE).toLong()
                val mevUnder = trackSlack(model, "mevUnder_$muscle")
                model.addGreaterOrEqual(
                    LinearExpr.weightedSum(arrayOf(weekly, mevUnder), longArrayOf(1L, 1L)),
                    minScaled,
                )
                weightedTerms.add(mevUnder to SynthesisObjectiveWeights.SPECIALIZED_MEV_SHORTFALL)
            }
        }

        _slots.forEach { sv ->
            val minSets = roleMinimum(sv.slot) ?: return@forEach
            val roleUnder = trackSlack(model, "roleUnder_${sv.slot.slotId}")
            val roleConstraint =
                model.addGreaterOrEqual(
                    LinearExpr.weightedSum(arrayOf(sv.x, roleUnder), longArrayOf(1L, 1L)),
                    minSets.toLong(),
                )
            sv.active?.let { active -> roleConstraint.onlyEnforceIf(active) }
            weightedTerms.add(roleUnder to SynthesisObjectiveWeights.ROLE_MINIMUM_SHORTFALL)
        }

        addOptionalSlotActivationPreferences(model, weightedTerms)
        addAntagonistMaintenanceBalance(model, weightedTerms)
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
        val sortedDays = trainingDays.sorted()
        for (index in 0 until sortedDays.lastIndex) {
            val earlierDay = sortedDays[index]
            val laterDay = sortedDays[index + 1]
            if (laterDay - earlierDay != 1) continue
            addGapPenalty(
                model = model,
                planI = planI,
                planJ = planJ,
                muscle = muscle,
                weight = weight,
                weightedTerms = weightedTerms,
                earlierDay = earlierDay,
                laterDay = laterDay,
                calendarGap = 1,
            )
            addGapPenalty(
                model = model,
                planI = planJ,
                planJ = planI,
                muscle = muscle,
                weight = weight,
                weightedTerms = weightedTerms,
                earlierDay = earlierDay,
                laterDay = laterDay,
                calendarGap = 1,
            )
        }
        if (sortedDays.size >= 2) {
            val wrapEarlier = sortedDays.last()
            val wrapLater = sortedDays.first()
            val wrapGap = 7 - wrapEarlier + wrapLater
            addGapPenalty(
                model = model,
                planI = planI,
                planJ = planJ,
                muscle = muscle,
                weight = weight,
                weightedTerms = weightedTerms,
                earlierDay = wrapEarlier,
                laterDay = wrapLater,
                calendarGap = wrapGap,
            )
            addGapPenalty(
                model = model,
                planI = planJ,
                planJ = planI,
                muscle = muscle,
                weight = weight,
                weightedTerms = weightedTerms,
                earlierDay = wrapEarlier,
                laterDay = wrapLater,
                calendarGap = wrapGap,
            )
        }
    }

    /**
     * Hard forbid: specialized muscles cannot be meaningfully exposed on two sessions
     * assigned to calendar-adjacent training days (Mon→Tue, Tue→Wed, Fri→Sat, Sun→Mon, etc.).
     */
    private fun addPriorityMuscleAdjacentForbids(
        model: CpModel,
        plans: List<SessionSlotPlan>,
        priorityMuscles: Set<String>,
    ) {
        if (priorityMuscles.isEmpty()) return
        val trainingDays = trainingWeekdayIndices(context.weekdays).sorted()
        for ((d1, d2) in calendarAdjacentTrainingDayPairs(trainingDays)) {
            for (muscle in priorityMuscles) {
                val sessions = plans.filter { plan -> maxSessionCreditScaled(plan, muscle) > 0L }
                for (planI in sessions) {
                    for (planJ in sessions) {
                        if (planI.position == planJ.position) continue
                        val creditI = sessionMuscleCreditVar(model, planI, muscle) ?: continue
                        val creditJ = sessionMuscleCreditVar(model, planJ, muscle) ?: continue
                        val meaningfulI = meaningfulExposureLiteral(model, creditI, muscle, planI.position)
                        val meaningfulJ = meaningfulExposureLiteral(model, creditJ, muscle, planJ.position)
                        val dayI = _dayVarByPosition.getValue(planI.position)
                        val dayJ = _dayVarByPosition.getValue(planJ.position)
                        val onConsecutive =
                            channelDayPair(model, dayI, dayJ, d1, d2, "hard_${muscle}_${planI.position}_${planJ.position}")
                        val onConsecutiveSwapped =
                            channelDayPair(
                                model,
                                dayJ,
                                dayI,
                                d1,
                                d2,
                                "hard_${muscle}_${planJ.position}_${planI.position}",
                            )
                        val onEitherConsecutive =
                            model.newBoolVar("hardEither_${muscle}_${d1}_${d2}_${planI.position}_${planJ.position}")
                        linkEitherConsecutive(model, onEitherConsecutive, onConsecutive, onConsecutiveSwapped)
                        model.addBoolOr(
                            arrayOf(
                                onEitherConsecutive.not(),
                                meaningfulI.not(),
                                meaningfulJ.not(),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun linkEitherConsecutive(
        model: CpModel,
        onEither: Literal,
        onConsecutive: Literal,
        onConsecutiveSwapped: Literal,
    ) {
        model.addBoolOr(arrayOf(onConsecutive, onConsecutiveSwapped)).onlyEnforceIf(onEither)
        model.addBoolAnd(arrayOf(onConsecutive.not(), onConsecutiveSwapped.not())).onlyEnforceIf(onEither.not())
    }

    /**
     * Extra penalty when a priority muscle is meaningfully exposed on two calendar-adjacent
     * training days (e.g. Mon pull + Tue push both stressing biceps).
     */
    private fun addConsecutiveTrainingDayExposurePenalties(
        model: CpModel,
        plans: List<SessionSlotPlan>,
        priorityMuscles: Set<String>,
        weightedTerms: MutableList<Pair<IntVar, Long>>,
    ) {
        val trainingDays = trainingWeekdayIndices(context.weekdays).sorted()
        for ((d1, d2) in calendarAdjacentTrainingDayPairs(trainingDays)) {
            for (muscle in priorityMuscles) {
                val sessions = plans.filter { plan -> maxSessionCreditScaled(plan, muscle) > 0L }
                for (planI in sessions) {
                    for (planJ in sessions) {
                        if (planI.position == planJ.position) continue
                        val creditI = sessionMuscleCreditVar(model, planI, muscle) ?: continue
                        val creditJ = sessionMuscleCreditVar(model, planJ, muscle) ?: continue
                        val meaningfulI = meaningfulExposureLiteral(model, creditI, muscle, planI.position)
                        val meaningfulJ = meaningfulExposureLiteral(model, creditJ, muscle, planJ.position)
                        val dayI = _dayVarByPosition.getValue(planI.position)
                        val dayJ = _dayVarByPosition.getValue(planJ.position)
                        val onConsecutiveDays =
                            channelDayPair(model, dayI, dayJ, d1, d2, "${muscle}_adj_${planI.position}_${planJ.position}")
                        val onConsecutiveDaysSwapped =
                            channelDayPair(model, dayJ, dayI, d1, d2, "${muscle}_adj_${planJ.position}_${planI.position}")
                        val onEitherConsecutive = model.newBoolVar("eitherAdj_${muscle}_${planI.position}_${planJ.position}")
                        linkEitherConsecutive(model, onEitherConsecutive, onConsecutiveDays, onConsecutiveDaysSwapped)
                        val slack = trackSlack(model, "rec_adj_${muscle}_${d1}_${d2}_${planI.position}_${planJ.position}")
                        val penalty = model.addGreaterOrEqual(slack, CONSECUTIVE_EXPOSURE_PENALTY)
                        penalty.onlyEnforceIf(onEitherConsecutive)
                        penalty.onlyEnforceIf(meaningfulI)
                        penalty.onlyEnforceIf(meaningfulJ)
                        weightedTerms.add(slack to SynthesisObjectiveWeights.CONSECUTIVE_TRAINING_DAY_EXPOSURE)
                    }
                }
            }
        }
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
                val coeff = VolumeAllocationCoefficients.scaledCoeff(sv.slot, muscle)
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

    private fun meaningfulExposureLiteral(
        model: CpModel,
        credit: IntVar,
        muscle: String,
        sessionPosition: Int,
    ): Literal {
        meaningfulExposureBySessionMuscle[sessionPosition to muscle]?.let { return it }
        val minScaled =
            (MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE * VolumeAllocationCoefficients.SCALE).toLong()
        val meaningful = model.newBoolVar("meaningful_${muscle}_${sessionPosition}")
        model.addGreaterOrEqual(credit, minScaled).onlyEnforceIf(meaningful)
        model.addLessOrEqual(credit, minScaled - 1).onlyEnforceIf(meaningful.not())
        meaningfulExposureBySessionMuscle[sessionPosition to muscle] = meaningful
        return meaningful
    }

    internal fun findPriorityAdjacentExposureViolations(
        solver: CpSolver,
        plans: List<SessionSlotPlan>,
        priorityMuscles: Set<String>,
    ): List<String> {
        if (priorityMuscles.isEmpty()) return emptyList()
        val minScaled =
            (MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE * VolumeAllocationCoefficients.SCALE).toLong()
        val trainingDays = trainingWeekdayIndices(context.weekdays).sorted()
        val violations = mutableListOf<String>()
        for ((d1, d2) in calendarAdjacentTrainingDayPairs(trainingDays)) {
            for (muscle in priorityMuscles) {
                for (planI in plans) {
                    for (planJ in plans) {
                        if (planI.position == planJ.position) continue
                        val creditI = sessionMuscleCredits[planI.position]?.get(muscle) ?: continue
                        val creditJ = sessionMuscleCredits[planJ.position]?.get(muscle) ?: continue
                        val dayI = solver.value(_dayVarByPosition.getValue(planI.position)).toInt()
                        val dayJ = solver.value(_dayVarByPosition.getValue(planJ.position)).toInt()
                        val onAdjacent =
                            dayI == d1 && dayJ == d2 || dayI == d2 && dayJ == d1
                        if (!onAdjacent) continue
                        val doseI = solver.value(creditI)
                        val doseJ = solver.value(creditJ)
                        if (doseI >= minScaled && doseJ >= minScaled) {
                            violations +=
                                "$muscle p${planI.position}(day=$dayI,dose=${doseI / 2.0})↔" +
                                "p${planJ.position}(day=$dayJ,dose=${doseJ / 2.0})"
                        }
                    }
                }
            }
        }
        return violations
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
    ) {
        val dayI = _dayVarByPosition.getValue(planI.position)
        val dayJ = _dayVarByPosition.getValue(planJ.position)
        val doseI = peakSessionDose(planI, muscle)
        val doseJ = peakSessionDose(planJ, muscle)
        val required = minimumRecoveryCalendarGap(muscle, doseI, doseJ)
        if (required <= 0 || calendarGap >= required) return
        val creditI = sessionMuscleCreditVar(model, planI, muscle) ?: return
        val creditJ = sessionMuscleCreditVar(model, planJ, muscle) ?: return
        val meaningfulI = meaningfulExposureLiteral(model, creditI, muscle, planI.position)
        val meaningfulJ = meaningfulExposureLiteral(model, creditJ, muscle, planJ.position)
        val shortfall = required - calendarGap
        val channelLabel = "${muscle}_${earlierDay}_${laterDay}_${planI.position}_${planJ.position}"
        val both = channelDayPair(model, dayI, dayJ, earlierDay, laterDay, channelLabel)
        val calendarAdjacent = calendarGap == 1
        val penaltyScale = if (calendarAdjacent) CALENDAR_ADJACENT_PENALTY_MULTIPLIER else 1
        val maxPenalty =
            (shortfall * shortfall * RECOVERY_GAP_PENALTY_BASE * penaltyScale)
                .toLong()
                .coerceAtMost(RECOVERY_SLACK_CAP)
        val prefix = if (weight == SynthesisObjectiveWeights.RECOVERY_PRIORITY) "rec_pri_" else "rec_gen_"
        val slack = trackSlack(model, "${prefix}${muscle}_${earlierDay}_${laterDay}_$calendarGap")
        val penalty = model.addGreaterOrEqual(slack, maxPenalty)
        penalty.onlyEnforceIf(both)
        penalty.onlyEnforceIf(meaningfulI)
        penalty.onlyEnforceIf(meaningfulJ)
        if (calendarAdjacent && calendarGap < required) {
            model.addBoolOr(arrayOf(both.not(), meaningfulI.not(), meaningfulJ.not()))
        }
        weightedTerms.add(slack to weight)
    }

    private fun channelDayPair(
        model: CpModel,
        dayI: IntVar,
        dayJ: IntVar,
        d1: Int,
        d2: Int,
        muscle: String,
    ): Literal {
        val onI = model.newBoolVar("onI_${muscle}_${d1}")
        model.addEquality(dayI, d1.toLong()).onlyEnforceIf(onI)
        model.addDifferent(dayI, d1.toLong()).onlyEnforceIf(onI.not())
        val onJ = model.newBoolVar("onJ_${muscle}_${d2}")
        model.addEquality(dayJ, d2.toLong()).onlyEnforceIf(onJ)
        model.addDifferent(dayJ, d2.toLong()).onlyEnforceIf(onJ.not())
        val both = model.newBoolVar("both_${muscle}_${d1}_${d2}")
        model.addBoolAnd(arrayOf(onI, onJ)).onlyEnforceIf(both)
        model.addBoolOr(arrayOf(onI.not(), onJ.not(), both))
        return both
    }

    private fun addSessionTypeAdjacencyPenalties(
        model: CpModel,
        plans: List<SessionSlotPlan>,
        weightedTerms: MutableList<Pair<IntVar, Long>>,
    ) {
        val trainingDays = trainingWeekdayIndices(context.weekdays)
        val adjacencyPairs =
            listOf(
                SessionTypeId.Lower to SessionTypeId.UpperPush,
                SessionTypeId.UpperPull to SessionTypeId.UpperPush,
                SessionTypeId.UpperPush to SessionTypeId.UpperPull,
            )
        for (i in plans.indices) {
            for (j in (i + 1) until plans.size) {
                val planI = plans[i]
                val planJ = plans[j]
                val typePair = planI.sessionType to planJ.sessionType
                val reversePair = planJ.sessionType to planI.sessionType
                if (typePair !in adjacencyPairs && reversePair !in adjacencyPairs) continue
                val dayI = _dayVarByPosition.getValue(planI.position)
                val dayJ = _dayVarByPosition.getValue(planJ.position)
                for (d1 in trainingDays) {
                    for (d2 in trainingDays) {
                        if (d1 == d2) continue
                        val gap = kotlin.math.abs(d2 - d1)
                        if (gap >= 2) continue
                        val both = channelDayPair(model, dayI, dayJ, d1, d2, "adj_${planI.position}_${planJ.position}")
                        val slack = trackSlack(model, "adj_${planI.position}_${planJ.position}_${d1}_${d2}")
                        model.addGreaterOrEqual(slack, 500L).onlyEnforceIf(both)
                        weightedTerms.add(slack to SynthesisObjectiveWeights.RECOVERY_PRIORITY)
                    }
                }
            }
        }
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
        weightedTerms.add(over to SynthesisObjectiveWeights.PUSH_PULL_IMBALANCE)
        weightedTerms.add(under to SynthesisObjectiveWeights.PUSH_PULL_IMBALANCE)
    }

    private fun addSessionFillPenalties(
        model: CpModel,
        plans: List<SessionSlotPlan>,
        weightedTerms: MutableList<Pair<IntVar, Long>>,
    ) {
        val sessionCap = sessionHardSetCapForLength(context.sessionLengthMinutes)?.toLong() ?: return
        val fillTarget =
            kotlin.math.round(sessionCap * SESSION_FILL_TARGET_RATIO).toLong().coerceAtMost(sessionCap)
        val targetSets = maxOf(MIN_SESSION_HARD_SETS.toLong(), fillTarget)
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
            weightedTerms.add(under to SynthesisObjectiveWeights.SESSION_FILL_SHORTFALL)
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
            weightedTerms.add(under to SynthesisObjectiveWeights.SESSION_SET_IMBALANCE)
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

    private fun addConsecutiveDayDomainConstraints(model: CpModel, plans: List<SessionSlotPlan>) {
        val trainingDays = trainingWeekdayIndices(context.weekdays).sorted()
        for ((d1, d2) in calendarAdjacentTrainingDayPairs(trainingDays)) {
            for (planI in plans) {
                for (planJ in plans) {
                    if (planI.position == planJ.position) continue
                    val dayI = _dayVarByPosition.getValue(planI.position)
                    val dayJ = _dayVarByPosition.getValue(planJ.position)
                    val onConsecutive =
                        channelDayPair(
                            model,
                            dayI,
                            dayJ,
                            d1,
                            d2,
                            "dom_${planI.position}_${planJ.position}",
                        )
                    val onConsecutiveSwapped =
                        channelDayPair(
                            model,
                            dayJ,
                            dayI,
                            d1,
                            d2,
                            "dom_${planJ.position}_${planI.position}",
                        )
                    val domainI = unifiedStructureState.domainVarByPosition.getValue(planI.position)
                    val domainJ = unifiedStructureState.domainVarByPosition.getValue(planJ.position)
                    forbidSameDomainOnConsecutiveDays(
                        model = model,
                        domainI = domainI,
                        domainJ = domainJ,
                        domain = SynthesisSessionDomain.Upper,
                        onConsecutive = onConsecutive,
                        onConsecutiveSwapped = onConsecutiveSwapped,
                        label = "upper_${planI.position}_${planJ.position}",
                    )
                    forbidSameDomainOnConsecutiveDays(
                        model = model,
                        domainI = domainI,
                        domainJ = domainJ,
                        domain = SynthesisSessionDomain.Lower,
                        onConsecutive = onConsecutive,
                        onConsecutiveSwapped = onConsecutiveSwapped,
                        label = "lower_${planI.position}_${planJ.position}",
                    )
                }
            }
        }
    }

    private fun forbidSameDomainOnConsecutiveDays(
        model: CpModel,
        domainI: IntVar,
        domainJ: IntVar,
        domain: SynthesisSessionDomain,
        onConsecutive: Literal,
        onConsecutiveSwapped: Literal,
        label: String,
    ) {
        val isDomainI = domainLiteral(model, domainI, domain, "is${domain.name}I_$label")
        val isDomainJ = domainLiteral(model, domainJ, domain, "is${domain.name}J_$label")
        val both = model.newBoolVar("both${domain.name}_$label")
        model.addBoolAnd(arrayOf(isDomainI, isDomainJ)).onlyEnforceIf(both)
        model.addBoolOr(arrayOf(isDomainI.not(), isDomainJ.not(), both))
        val onEitherConsecutive = model.newBoolVar("eitherConsecutive_$label")
        linkEitherConsecutive(model, onEitherConsecutive, onConsecutive, onConsecutiveSwapped)
        model.addBoolOr(arrayOf(onEitherConsecutive.not(), both.not()))
    }

    private fun domainLiteral(
        model: CpModel,
        domainVar: IntVar,
        domain: SynthesisSessionDomain,
        name: String,
    ): Literal {
        val literal = model.newBoolVar(name)
        model.addEquality(domainVar, domain.ordinal.toLong()).onlyEnforceIf(literal)
        model.addDifferent(domainVar, domain.ordinal.toLong()).onlyEnforceIf(literal.not())
        return literal
    }

    private fun addAntagonistMaintenanceBalance(
        model: CpModel,
        weightedTerms: MutableList<Pair<IntVar, Long>>,
    ) {
        for (pair in ANTAGONIST_MAINTENANCE_PAIRS) {
            if (!shouldBalanceAntagonistPair(pair.weaker, pair.stronger)) continue
            val weakerCredit = weeklyCreditExpr(pair.weaker) ?: continue
            val strongerCredit = weeklyCreditExpr(pair.stronger) ?: continue
            val wrongWayExcess = trackSlack(model, "antWrong_${pair.label}")
            val rightWayExcess = trackSlack(model, "antRight_${pair.label}")
            model.addEquality(
                LinearExpr.weightedSum(
                    arrayOf(weakerCredit, strongerCredit, wrongWayExcess, rightWayExcess),
                    longArrayOf(1L, -1L, -1L, 1L),
                ),
                0L,
            )
            val wrongWeight =
                SynthesisObjectiveWeights.ANTAGONIST_WRONG_WAY_EXCESS +
                    SynthesisObjectiveWeights.ANTAGONIST_PAIR_CLOSENESS
            val rightWeight =
                SynthesisObjectiveWeights.ANTAGONIST_RIGHT_WAY_EXCESS +
                    SynthesisObjectiveWeights.ANTAGONIST_PAIR_CLOSENESS
            weightedTerms.add(wrongWayExcess to wrongWeight)
            weightedTerms.add(rightWayExcess to rightWeight)
        }
    }

    /**
     * Balance antagonist pairs when both muscles share the same specialization tier.
     * Skips when only one side is specialized (e.g. chest maintenance + back specialize).
     */
    private fun shouldBalanceAntagonistPair(muscleA: String, muscleB: String): Boolean {
        val levelA = context.specialization[muscleA] ?: "Maintenance"
        val levelB = context.specialization[muscleB] ?: "Maintenance"
        return levelA == levelB
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
                val diffOver = trackSlack(model, "diffOver_${specializedMuscles[i]}_${specializedMuscles[j]}")
                val diffUnder = trackSlack(model, "diffUnder_${specializedMuscles[i]}_${specializedMuscles[j]}")
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
            val coeff = VolumeAllocationCoefficients.scaledCoeff(sv.slot, muscle)
            total += cap * coeff
        }
        return total
    }

    private fun peakSessionDose(plan: SessionSlotPlan, muscle: String): Double =
        maxSessionCreditScaled(plan, muscle).toDouble() / VolumeAllocationCoefficients.SCALE

    private fun addOptionalSlotActivationPreferences(
        model: CpModel,
        weightedTerms: MutableList<Pair<IntVar, Long>>,
    ) {
        _slots.forEach { sv ->
            val active = sv.active ?: return@forEach
            if (!prefersSlotActivation(sv.slot, context)) return@forEach
            val inactivePenalty = model.newIntVar(0L, 1L, "inactivePref_${sv.slot.slotId}")
            model.addEquality(inactivePenalty, 0).onlyEnforceIf(active)
            model.addEquality(inactivePenalty, 1).onlyEnforceIf(active.not())
            weightedTerms.add(inactivePenalty to SynthesisObjectiveWeights.OPTIONAL_SLOT_INACTIVE_PREFERENCE)
        }
    }

    private fun roleMinimum(slot: FilledSlot): Int? =
        when (slot.role) {
            SlotRole.Anchor -> ANCHOR_MIN_SETS
            SlotRole.SecondaryCompound -> SECONDARY_COMPOUND_MIN_SETS
            SlotRole.Isolation ->
                when {
                    slot.isCrossDomainTouch &&
                        (context.specialization[slot.primaryMuscle] ?: "Maintenance") == "Specialize" ->
                        MIN_TOUCH_DIRECT_SETS
                    (context.specialization[slot.primaryMuscle] ?: "Maintenance") == "Specialize" ->
                        MIN_SPECIALIZED_DIRECT_SETS
                    else -> null
                }
            SlotRole.Optional -> null
        }

    private fun weeklyCreditExpr(muscle: String): LinearExpr? {
        val terms =
            _slots.mapNotNull { sv ->
                val c = VolumeAllocationCoefficients.scaledCoeff(sv.slot, muscle)
                if (c == 0L) null else sv.x to c
            }
        if (terms.isEmpty()) return null
        return LinearExpr.weightedSum(
            terms.map { it.first }.toTypedArray(),
            terms.map { it.second }.toLongArray(),
        )
    }

    private fun trackSlack(model: CpModel, name: String): IntVar {
        val slack = model.newIntVar(0L, MAX_OVER, name)
        objectiveSlacks[name] = slack
        return slack
    }

    private companion object {
        const val COMPOUND_SETS_PER_SLOT_CAP = 4
        const val ANCHOR_MIN_SETS = 3
        const val SECONDARY_COMPOUND_MIN_SETS = 2
        const val AIM_EQUALITY_TOLERANCE = 0.5
        const val MAX_OVER = 1000L
        const val RECOVERY_SLACK_CAP = 10_000L
        const val RECOVERY_GAP_PENALTY_BASE = 80
        const val CALENDAR_ADJACENT_PENALTY_MULTIPLIER = 10
        const val CONSECUTIVE_EXPOSURE_PENALTY = 10_000L
        const val MIN_SESSION_HARD_SETS = 8
        const val MIN_ACTIVE_LIFTS_PER_SESSION = 4
        /** Fraction of per-session hard-set cap to target (65% of 20 → 13 sets on 60 min days). */
        const val SESSION_FILL_TARGET_RATIO = 0.65
        val LOWER_LEG_ANCHOR_PATTERNS = setOf("SquatPattern", "HipHinge")
        val ANTAGONIST_MAINTENANCE_PAIRS =
            listOf(
                AntagonistMaintenancePair("Chest", "Back", "chest_back"),
                AntagonistMaintenancePair("Quad", "Hamstring", "quad_ham"),
            )
    }

    private data class AntagonistMaintenancePair(
        val weaker: String,
        val stronger: String,
        val label: String,
    )
}

internal fun recoveryPriorityMuscles(context: ProgramGenerationContext): Set<String> =
    context.specialization
        .filterValues { level -> level == "Specialize" || level == "MinimumVolume" }
        .keys

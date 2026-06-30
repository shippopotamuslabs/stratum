@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.prescription

import com.stratum.domain.program.generation.context.PRIMARY_SET_CREDIT
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.reference.MIN_SPECIALIZED_DIRECT_SETS
import com.stratum.domain.program.generation.reference.PATTERN_SECONDARY_MUSCLE_CREDITS
import com.stratum.domain.program.generation.reference.SESSION_MUSCLE_SET_CAP
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.reference.sessionHardSetCapForLength
import com.stratum.domain.program.generation.split.MuscleLengthBiasPreference
import com.stratum.domain.program.generation.split.ResolvedSessionPlan
import com.stratum.domain.program.generation.split.SessionTypeId
import com.stratum.domain.program.generation.split.SiblingVariant
import com.stratum.domain.program.generation.split.SlotRole
import com.stratum.domain.program.generation.split.WorkoutSlotTemplate
import com.stratum.domain.program.generation.volume.WeeklyVolumeTarget
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets

data class FilledSlot(
    val slotId: String,
    val role: SlotRole,
    val pattern: String,
    val primaryMuscle: String,
    val repProfile: RpRepProfile,
    val lengthBiasPreference: MuscleLengthBiasPreference? = null,
    val siblingVariant: SiblingVariant? = null,
    var setCount: Int,
) {
    fun toWorkoutSlotTemplate(): WorkoutSlotTemplate =
        WorkoutSlotTemplate(
            slotId = slotId,
            role = role,
            pattern = pattern,
            primaryMuscle = primaryMuscle,
            repProfile = repProfile,
            lengthBiasPreference = lengthBiasPreference,
            siblingVariant = siblingVariant,
        )
}

data class SessionSlotPlan(
    val sessionType: SessionTypeId,
    val name: String,
    val slots: List<WorkoutSlotTemplate>,
    val siblingIndex: Int,
    val position: Int,
    val filledSlots: MutableList<FilledSlot>,
)

/** Max sets on one isolation slot per session — spreads specialized volume across sessions. */
private const val SPECIALIZED_SETS_PER_SLOT_CAP = 6

/** Max sets on one compound slot — prevents 6-set bench stacks on a single lift. */
private const val COMPOUND_SETS_PER_SLOT_CAP = 4

/** Minimum sets on anchor compounds (squat/hinge/press) when the slot is active. */
private const val ANCHOR_MIN_SETS = 3

private fun WorkoutSlotTemplate.toFilledSlot(setCount: Int = 0): FilledSlot =
    FilledSlot(
        slotId = slotId,
        role = role,
        pattern = pattern,
        primaryMuscle = primaryMuscle,
        repProfile = repProfile,
        lengthBiasPreference = lengthBiasPreference,
        siblingVariant = siblingVariant,
        setCount = setCount,
    )

private fun sessionHardSetCap(context: ProgramGenerationContext): Int? =
    sessionHardSetCapForLength(context.sessionLengthMinutes)

internal fun muscleCreditsInSession(plan: SessionSlotPlan, muscle: String): Double {
    var total = 0.0
    for (slot in plan.filledSlots) {
        if (slot.setCount <= 0) continue
        if (slot.primaryMuscle == muscle) {
            total += slot.setCount * PRIMARY_SET_CREDIT
            continue
        }
        for (secondary in PATTERN_SECONDARY_MUSCLE_CREDITS[slot.pattern].orEmpty()) {
            if (secondary.muscle == muscle) {
                total += slot.setCount * secondary.creditPerSet
            }
        }
    }
    return total
}

private fun allocationOrder(
    weeklyTargets: WeeklyVolumeTargets,
    context: ProgramGenerationContext,
): List<Pair<String, WeeklyVolumeTarget>> =
    weeklyTargets.entries.sortedWith { (muscleA, _), (muscleB, _) ->
        val aSpec = (context.specialization[muscleA] ?: "Maintenance") == "Specialize"
        val bSpec = (context.specialization[muscleB] ?: "Maintenance") == "Specialize"
        when {
            aSpec != bSpec -> if (aSpec) -1 else 1
            else -> muscleA.compareTo(muscleB)
        }
    }.map { it.key to it.value }

fun allocateSlotSetCounts(
    sessions: List<ResolvedSessionPlan>,
    weeklyTargets: WeeklyVolumeTargets,
    context: ProgramGenerationContext,
    port: SlotSetAllocationPort? = null,
): List<SessionSlotPlan> {
    if (port != null) {
        val plans = buildEmptySessionPlans(sessions, context)
        val result = port.allocate(plans, weeklyTargets, context)
        if (result is SlotSetAllocationResult.Success) {
            // Post-solver safety net: the solver owns caps, minimums, and
            // co-specialization balance, but push/pull structural balance and
            // full-body short-session layout are not modeled. Run the cheap
            // passes the solver does not subsume, then enforce caps again.
            return applyPostSolverSafety(result.plans, context)
        }
        // Fallback: solver reported infeasible/timeout — rebuild empty plans
        // (the solver may have mutated setCount before failing) and run greedy.
        return allocateSlotSetCountsGreedy(buildEmptySessionPlans(sessions, context), weeklyTargets, context)
    }
    return allocateSlotSetCountsGreedy(buildEmptySessionPlans(sessions, context), weeklyTargets, context)
}

/**
 * Post-solver safety passes: push/pull + leg pair balance, upper-day pull
 * minimum, anchor floor, session/muscle cap enforcement, and full-body
 * short-session layout. The CP-SAT solver does not model push/pull structural
 * balance or the fixed 30-min full-body override, so these run after every
 * successful solver result. Cheap and idempotent when the solver already
 * satisfied them.
 */
private fun applyPostSolverSafety(
    plans: List<SessionSlotPlan>,
    context: ProgramGenerationContext,
): List<SessionSlotPlan> {
    balanceSessionSlotSets(plans, context)
    ensureAnchorMinimumSets(plans)
    enforceSessionCaps(plans, context)
    applyFullBodyShortSessionLayout(plans, context)
    return plans
}

/**
 * Greedy muscle-by-muscle round-robin allocator with structural rebalancing.
 * Used as the [allocateSlotSetCounts] fallback when no CP-SAT port is wired or
 * the solver reports infeasibility. Receives already-built [SessionSlotPlan]s
 * (optional slots filtered, `setCount = 0`) and mutates them in place.
 */
private fun allocateSlotSetCountsGreedy(
    plans: List<SessionSlotPlan>,
    weeklyTargets: WeeklyVolumeTargets,
    context: ProgramGenerationContext,
): List<SessionSlotPlan> {

    for ((muscle, target) in allocationOrder(weeklyTargets, context)) {
        if (target.aim <= 0) continue
        val isSpecialized = (context.specialization[muscle] ?: "Maintenance") == "Specialize"
        val slotsWithMuscle = mutableListOf<Triple<SessionSlotPlan, FilledSlot, Int>>()
        plans.forEach { plan ->
            plan.filledSlots.forEachIndexed { idx, slot ->
                if (slot.primaryMuscle == muscle) {
                    slotsWithMuscle.add(Triple(plan, slot, idx))
                }
            }
        }
        if (slotsWithMuscle.isEmpty()) continue

        var remaining = kotlin.math.round(target.aim).toInt()
        val primarySlots = slotsWithMuscle.filter { it.second.primaryMuscle == muscle }
        val targets = if (primarySlots.isNotEmpty()) primarySlots else slotsWithMuscle

        if (isSpecialized) {
            distributeWithPerSlotCap(targets, remaining, SPECIALIZED_SETS_PER_SLOT_CAP, muscle)
        } else {
            distributeWithPerSlotCap(targets, remaining, COMPOUND_SETS_PER_SLOT_CAP, muscle)
        }
    }

    balanceSessionSlotSets(plans, context)
    ensureAnchorMinimumSets(plans)
    enforceSessionCaps(plans, context)
    applyFullBodyShortSessionLayout(plans, context)
    return plans
}

/**
 * Builds [SessionSlotPlan]s from resolved sessions with optional slots filtered
 * via [shouldIncludeOptional] and every [FilledSlot] instantiated at
 * `setCount = 0`. Shared by [allocateSlotSetCounts] (greedy) and the CP-SAT
 * solver port so both start from identical plan structures.
 */
fun buildEmptySessionPlans(
    sessions: List<ResolvedSessionPlan>,
    context: ProgramGenerationContext,
): List<SessionSlotPlan> =
    sessions.map { session ->
        SessionSlotPlan(
            sessionType = session.sessionType,
            name = session.name,
            slots = session.slots,
            siblingIndex = session.siblingIndex,
            position = session.position,
            filledSlots = session.slots
                .filter { s -> s.role != SlotRole.Optional || shouldIncludeOptional(s, context) }
                .map { it.toFilledSlot(0) }
                .toMutableList(),
        )
    }

private data class SlotTarget(val plan: SessionSlotPlan, val slot: FilledSlot)

private fun distributeWithPerSlotCap(
    targets: List<Triple<SessionSlotPlan, FilledSlot, Int>>,
    remaining: Int,
    perSlotCap: Int,
    muscle: String,
) {
    var slotIdx = 0
    var guard = 0
    var left = remaining
    val slotTargets = targets.map { SlotTarget(it.first, it.second) }
    while (left > 0 && slotTargets.isNotEmpty() && guard < slotTargets.size * 40) {
        guard++
        val entry = slotTargets[slotIdx % slotTargets.size]
        if (muscleCreditsInSession(entry.plan, muscle) >= SESSION_MUSCLE_SET_CAP) {
            slotIdx++
            continue
        }
        val headroom = minOf(
            perSlotCap - entry.slot.setCount,
            (SESSION_MUSCLE_SET_CAP - muscleCreditsInSession(entry.plan, muscle)).toInt(),
        )
        if (headroom <= 0) {
            slotIdx++
            continue
        }
        val add = minOf(left, headroom)
        entry.slot.setCount += add
        left -= add
        slotIdx++
    }
}

private fun ensureAnchorMinimumSets(plans: List<SessionSlotPlan>) {
    for (plan in plans) {
        for (slot in plan.filledSlots) {
            if (slot.role == SlotRole.Anchor && slot.setCount > 0 && slot.setCount < ANCHOR_MIN_SETS) {
                slot.setCount = ANCHOR_MIN_SETS
            }
        }
    }
}

private fun enforceSessionCaps(plans: List<SessionSlotPlan>, context: ProgramGenerationContext) {
    val cap = sessionHardSetCap(context)
    for (plan in plans) {
        if (cap != null) {
            var totalSets = plan.filledSlots.sumOf { it.setCount }
            while (totalSets > cap && plan.filledSlots.isNotEmpty()) {
                val drop = slotToDropForSessionTimeCap(plan, context) ?: break
                drop.setCount -= 1
                totalSets -= 1
            }
        }
        for (muscle in musclesNeedingCapEnforcement(plan)) {
            while (muscleCreditsInSession(plan, muscle) > SESSION_MUSCLE_SET_CAP) {
                val drop = slotToTrimForMuscleCap(plan, muscle, context) ?: break
                drop.setCount -= 1
            }
        }
    }
}

private fun musclesNeedingCapEnforcement(plan: SessionSlotPlan): Set<String> {
    val muscles = plan.filledSlots.map { it.primaryMuscle }.toMutableSet()
    for (slot in plan.filledSlots) {
        for (secondary in PATTERN_SECONDARY_MUSCLE_CREDITS[slot.pattern].orEmpty()) {
            muscles.add(secondary.muscle)
        }
    }
    return muscles
}

private fun specializedMuscles(context: ProgramGenerationContext): Set<String> {
    val out = mutableSetOf<String>()
    for ((muscle, level) in context.specialization) {
        if (level == "Specialize") out.add(muscle)
    }
    return out
}

private fun slotToDropForSessionTimeCap(
    plan: SessionSlotPlan,
    context: ProgramGenerationContext,
): FilledSlot? {
    val specialized = specializedMuscles(context)
    val optional = plan.filledSlots.find {
        it.role == SlotRole.Optional && it.setCount > 0 && it.primaryMuscle !in specialized
    }
    if (optional != null) return optional

    val specializedOptional = plan.filledSlots.find {
        it.role == SlotRole.Optional &&
            it.setCount > MIN_SPECIALIZED_DIRECT_SETS &&
            it.primaryMuscle in specialized
    }
    if (specializedOptional != null) return specializedOptional

    val maintenanceIso = plan.filledSlots.find {
        it.role == SlotRole.Isolation && it.setCount > 0 && it.primaryMuscle !in specialized
    }
    if (maintenanceIso != null) return maintenanceIso

    val maintenanceSecondary = plan.filledSlots
        .asReversed()
        .find {
            it.setCount > 0 && it.role == SlotRole.SecondaryCompound && it.primaryMuscle !in specialized
        }
    if (maintenanceSecondary != null) return maintenanceSecondary

    val specializedAboveMin = plan.filledSlots.find {
        it.setCount > MIN_SPECIALIZED_DIRECT_SETS &&
            it.primaryMuscle in specialized &&
            (it.role == SlotRole.Isolation || it.role == SlotRole.Anchor)
    }
    if (specializedAboveMin != null) return specializedAboveMin

    val anchorWithSets = plan.filledSlots.find { it.role == SlotRole.Anchor && it.setCount > 1 }
    if (anchorWithSets != null) return anchorWithSets

    return plan.filledSlots.find { it.setCount > 0 }
}

private fun slotToTrimForMuscleCap(
    plan: SessionSlotPlan,
    muscle: String,
    context: ProgramGenerationContext,
): FilledSlot? {
    val isSpecialized = (context.specialization[muscle] ?: "Maintenance") == "Specialize"
    val candidates = plan.filledSlots.filter { slot ->
        if (slot.setCount <= 0) return@filter false
        if (slot.primaryMuscle == muscle) return@filter true
        PATTERN_SECONDARY_MUSCLE_CREDITS[slot.pattern].orEmpty().any { it.muscle == muscle }
    }

    return candidates.minByOrNull { trimScore(it, muscle, isSpecialized) }
}

private fun trimScore(slot: FilledSlot, muscle: String, isSpecializedMuscle: Boolean): Int {
    val isPrimary = slot.primaryMuscle == muscle
    val isSecondary = !isPrimary &&
        PATTERN_SECONDARY_MUSCLE_CREDITS[slot.pattern].orEmpty().any { it.muscle == muscle }

    if (isSpecializedMuscle) {
        if (isSecondary) return 0
        if (isPrimary && slot.role == SlotRole.Isolation) return 3
        if (isPrimary && slot.role == SlotRole.Anchor) return 5
        return 2
    }
    if (isPrimary && slot.role != SlotRole.Anchor) return 0
    if (isPrimary && slot.role == SlotRole.Anchor) return 4
    return 1
}

private fun shouldIncludeOptional(slot: WorkoutSlotTemplate, context: ProgramGenerationContext): Boolean {
    if ((context.specialization[slot.primaryMuscle] ?: "Maintenance") == "Specialize") {
        return true
    }
    if (context.sessionLengthMinutes == null) return true
    return context.sessionLengthMinutes >= 60
}

private fun findSlotByPattern(
    plan: SessionSlotPlan,
    pattern: String,
    role: SlotRole? = null,
): FilledSlot? = plan.filledSlots.find { it.pattern == pattern && (role == null || it.role == role) }

private fun sessionTotalSets(plan: SessionSlotPlan): Int =
    plan.filledSlots.sumOf { it.setCount }

private fun transferSets(from: FilledSlot, to: FilledSlot, count: Int): Int {
    val moved = minOf(count, from.setCount)
    if (moved <= 0) return 0
    from.setCount -= moved
    to.setCount += moved
    return moved
}

/** Rebalance stacked push/pull and leg pairs; prioritize specialized slots on push days. */
private fun balanceSessionSlotSets(plans: List<SessionSlotPlan>, context: ProgramGenerationContext) {
    val cap = sessionHardSetCap(context)
    for (plan in plans) {
        balancePushPullPair(plan, "HorizontalPress", "HorizontalPull")
        balancePushPullPair(plan, "HorizontalPress", "VerticalPull")
        balanceLegPair(plan)
        ensureUpperDayPullMinimum(plan)
        if (cap != null) {
            while (sessionTotalSets(plan) > cap) {
                val drop = slotToDropForSessionTimeCap(plan, context)
                if (drop == null || drop.setCount <= 0) break
                drop.setCount -= 1
            }
        }
    }
}

private fun balancePushPullPair(plan: SessionSlotPlan, pushPattern: String, pullPattern: String) {
    val push = findSlotByPattern(plan, pushPattern) ?: return
    val pull = findSlotByPattern(plan, pullPattern) ?: return
    if (push.setCount <= 2 || pull.setCount > 0) return

    val move = minOf(push.setCount / 2, push.setCount - 2, COMPOUND_SETS_PER_SLOT_CAP)
    if (move >= 2) {
        transferSets(push, pull, move)
    } else if (push.setCount >= 3) {
        transferSets(push, pull, 1)
    }
}

private fun balanceLegPair(plan: SessionSlotPlan) {
    val squat = findSlotByPattern(plan, "SquatPattern", SlotRole.Anchor)
        ?: findSlotByPattern(plan, "SquatPattern")
        ?: return
    val hinge = findSlotByPattern(plan, "HipHinge") ?: return
    if (squat.setCount <= 3 || hinge.setCount > 0) return

    val move = minOf(2, squat.setCount - ANCHOR_MIN_SETS)
    if (move > 0) transferSets(squat, hinge, move)
}

private fun armsSpecializedInContext(context: ProgramGenerationContext): Boolean =
    (context.specialization["Bicep"] ?: "Maintenance") == "Specialize" ||
        (context.specialization["Tricep"] ?: "Maintenance") == "Specialize"

private fun applyFullBodyShortSessionLayout(plans: List<SessionSlotPlan>, context: ProgramGenerationContext) {
    for (plan in plans) {
        capFullBodyArmVolumeOnShortSessions(plan, context)
    }
}

private fun capFullBodyArmVolumeOnShortSessions(plan: SessionSlotPlan, context: ProgramGenerationContext) {
    if (plan.sessionType != SessionTypeId.FullBody || !armsSpecializedInContext(context)) return
    val cap = sessionHardSetCap(context)
    if (cap == null || cap > 12) return

    val bicep = plan.filledSlots.find { it.primaryMuscle == "Bicep" }
    val tricep = plan.filledSlots.find { it.primaryMuscle == "Tricep" }
    val press = findSlotByPattern(plan, "HorizontalPress")
    val pull = findSlotByPattern(plan, "HorizontalPull")
    val squat = findSlotByPattern(plan, "SquatPattern")

    if (bicep != null) bicep.setCount = 2
    if (tricep != null) tricep.setCount = 2
    if (press != null) press.setCount = 2
    if (pull != null) pull.setCount = 2
    if (squat != null) squat.setCount = 2

    for (slot in plan.filledSlots) {
        if (slot === bicep || slot === tricep || slot === press || slot === pull || slot === squat) continue
        slot.setCount = 0
    }
}

private fun ensureUpperDayPullMinimum(plan: SessionSlotPlan) {
    if (plan.sessionType != SessionTypeId.Upper) return
    val press = findSlotByPattern(plan, "HorizontalPress") ?: return
    val pull = findSlotByPattern(plan, "VerticalPull") ?: findSlotByPattern(plan, "HorizontalPull") ?: return
    if (press.setCount == 0 || pull.setCount >= 2) return
    if (press.setCount <= 2) return
    transferSets(press, pull, minOf(2 - pull.setCount, press.setCount - 2))
}

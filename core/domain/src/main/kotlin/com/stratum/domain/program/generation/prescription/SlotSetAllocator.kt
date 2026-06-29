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

/** Minimum direct arm isolation on a dedicated push/pull day when compounds also train that arm. */
private const val MIN_ARM_ISO_ON_SPLIT_DAY = 3

/** Minimum sets on a secondary compound (e.g. vertical press) when the session is active. */
private const val SECONDARY_COMPOUND_MIN_SETS = 2

/** Max set-count spread between sibling lower days before rebalancing. */
private const val LOWER_SESSION_SPREAD_TARGET = 3

private val LOWER_SLOT_BALANCE_ORDER = listOf("squat", "hinge", "quad-iso", "ham-iso", "calf")

private val LOWER_FILL_PATTERN_ORDER = listOf(
    "SquatPattern",
    "HipHinge",
    "KneeExtension",
    "KneeFlexion",
    "PlantarFlexion",
)

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
): List<SessionSlotPlan> {
    val plans = sessions.map { session ->
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
    rebalanceArmSpecSplitSessions(plans, context)
    balanceLowerSessionSpread(plans, context)
    fillLowerSessionsToSessionCap(plans, weeklyTargets, context)
    ensureAnchorMinimumSets(plans)
    enforceSessionCaps(plans, context)
    applyFullBodyShortSessionLayout(plans, context)
    return plans
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

private fun findSlot(plan: SessionSlotPlan, slotId: String): FilledSlot? =
    plan.filledSlots.find { it.slotId == slotId }

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

private fun perSlotSetCap(slot: FilledSlot): Int =
    if (slot.role == SlotRole.Anchor || slot.role == SlotRole.SecondaryCompound) {
        COMPOUND_SETS_PER_SLOT_CAP
    } else {
        SPECIALIZED_SETS_PER_SLOT_CAP
    }

private fun minSetsForSlot(slot: FilledSlot): Int =
    if (slot.role == SlotRole.Anchor) ANCHOR_MIN_SETS else 0

private fun weeklyPrimarySetsForMuscle(plans: List<SessionSlotPlan>, muscle: String): Int {
    var total = 0
    for (plan in plans) {
        for (slot in plan.filledSlots) {
            if (slot.primaryMuscle == muscle) total += slot.setCount
        }
    }
    return total
}

/** Even out Lower A / Lower B totals — arm specialization does not affect leg weekly targets. */
private fun balanceLowerSessionSpread(plans: List<SessionSlotPlan>, context: ProgramGenerationContext) {
    val cap = sessionHardSetCap(context)
    val lowerPlans = plans.filter { it.sessionType == SessionTypeId.Lower }
    if (lowerPlans.size < 2 || cap == null) return

    repeat(100) {
        val totals = lowerPlans.map { sessionTotalSets(it) }
        val maxTotal = totals.maxOrNull() ?: return
        val minTotal = totals.minOrNull() ?: return
        if (maxTotal - minTotal <= LOWER_SESSION_SPREAD_TARGET) return

        val maxPlan = lowerPlans[totals.indexOf(maxTotal)]
        val minPlan = lowerPlans[totals.indexOf(minTotal)]
        if (minTotal >= cap) return

        var moved = false
        for (slotId in LOWER_SLOT_BALANCE_ORDER) {
            val from = findSlot(maxPlan, slotId) ?: continue
            val to = findSlot(minPlan, slotId) ?: continue
            if (from.setCount <= minSetsForSlot(from)) continue
            if (to.setCount >= perSlotSetCap(to)) continue
            from.setCount -= 1
            to.setCount += 1
            moved = true
            break
        }
        if (!moved) return
    }
}

/** Top up under-filled lower days toward the session set cap without exceeding weekly muscle max. */
private fun fillLowerSessionsToSessionCap(
    plans: List<SessionSlotPlan>,
    weeklyTargets: WeeklyVolumeTargets,
    context: ProgramGenerationContext,
) {
    val cap = sessionHardSetCap(context) ?: return

    for (plan in plans.filter { it.sessionType == SessionTypeId.Lower }) {
        repeat(200) {
            if (sessionTotalSets(plan) >= cap) return@repeat
            var added = false
            for (pattern in LOWER_FILL_PATTERN_ORDER) {
                val slot = findSlotByPattern(plan, pattern) ?: continue
                if (slot.setCount >= perSlotSetCap(slot)) continue

                val target = weeklyTargets[slot.primaryMuscle]
                if (target != null && weeklyPrimarySetsForMuscle(plans, slot.primaryMuscle) >= target.max) {
                    continue
                }
                if (muscleCreditsInSession(plan, slot.primaryMuscle) >= SESSION_MUSCLE_SET_CAP) continue

                slot.setCount += 1
                added = true
                break
            }
            if (!added) return@repeat
        }
    }
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

/**
 * On push/pull split days, direct arm isolation can consume the whole per-session muscle
 * credit budget and zero out compounds (presses credit triceps; rows credit biceps).
 * Reserve compound minimums first, then cap arm isolation to remaining headroom.
 */
private fun rebalanceArmSpecSplitSessions(plans: List<SessionSlotPlan>, context: ProgramGenerationContext) {
    val dualArmSpecOnUpper =
        (context.specialization["Bicep"] ?: "Maintenance") == "Specialize" &&
            (context.specialization["Tricep"] ?: "Maintenance") == "Specialize"

    for (plan in plans) {
        when {
            plan.sessionType == SessionTypeId.UpperPush &&
                (context.specialization["Tricep"] ?: "Maintenance") == "Specialize" ->
                rebalanceUpperPushArmSpec(plan, context)
            plan.sessionType == SessionTypeId.UpperPull &&
                (context.specialization["Bicep"] ?: "Maintenance") == "Specialize" ->
                rebalanceUpperPullArmSpec(plan, context)
            plan.sessionType == SessionTypeId.Upper ->
                rebalanceCombinedUpperArmSpec(plan, context)
        }
    }

    if (dualArmSpecOnUpper) {
        balanceDualArmSpecAcrossUpperPlans(plans)
    }
}

internal fun secondaryCreditsForMuscle(plan: SessionSlotPlan, muscle: String): Double {
    var total = 0.0
    for (slot in plan.filledSlots) {
        if (slot.setCount <= 0 || slot.primaryMuscle == muscle) continue
        for (secondary in PATTERN_SECONDARY_MUSCLE_CREDITS[slot.pattern].orEmpty()) {
            if (secondary.muscle == muscle) {
                total += slot.setCount * secondary.creditPerSet
            }
        }
    }
    return total
}

private fun transferToReachMinimum(
    from: FilledSlot?,
    to: FilledSlot?,
    minimum: Int,
    floorForDonor: Int,
) {
    if (from == null || to == null) return
    val needed = minimum - to.setCount
    if (needed <= 0) return
    transferSets(from, to, minOf(needed, maxOf(0, from.setCount - floorForDonor)))
}

private fun capDirectArmIsolation(
    plan: SessionSlotPlan,
    armMuscle: String,
    isolationPattern: String,
) {
    val isolation = findSlotByPattern(plan, isolationPattern, SlotRole.Isolation) ?: return
    if (isolation.setCount <= 0) return

    val maxDirect = kotlin.math.floor(SESSION_MUSCLE_SET_CAP - secondaryCreditsForMuscle(plan, armMuscle)).toInt()
    if (maxDirect <= 0) {
        isolation.setCount = 0
        return
    }
    isolation.setCount = minOf(isolation.setCount, maxDirect)
}

private fun rebalanceUpperPushArmSpec(plan: SessionSlotPlan, context: ProgramGenerationContext) {
    val tricep = findSlotByPattern(plan, "ElbowExtension", SlotRole.Isolation)
    val hPress = findSlotByPattern(plan, "HorizontalPress")
    val incline = findSlotByPattern(plan, "InclinePress")
    val vPress = findSlotByPattern(plan, "VerticalPress")
    val latRaise = findSlotByPattern(plan, "ShoulderAbduction", SlotRole.Isolation)

    transferToReachMinimum(tricep, hPress, ANCHOR_MIN_SETS, MIN_ARM_ISO_ON_SPLIT_DAY)
    transferToReachMinimum(tricep, incline, SECONDARY_COMPOUND_MIN_SETS, MIN_ARM_ISO_ON_SPLIT_DAY)
    transferToReachMinimum(tricep, vPress, SECONDARY_COMPOUND_MIN_SETS, MIN_ARM_ISO_ON_SPLIT_DAY)
    transferToReachMinimum(tricep, latRaise, SECONDARY_COMPOUND_MIN_SETS, MIN_ARM_ISO_ON_SPLIT_DAY)

    capDirectArmIsolation(plan, "Tricep", "ElbowExtension")

    val cap = sessionHardSetCap(context)
    if (cap != null) {
        while (sessionTotalSets(plan) > cap) {
            val drop = slotToDropForSessionTimeCap(plan, context)
            if (drop == null || drop.setCount <= 0) break
            drop.setCount -= 1
        }
    }
}

private fun rebalanceUpperPullArmSpec(plan: SessionSlotPlan, context: ProgramGenerationContext) {
    val bicep = findSlotByPattern(plan, "ElbowFlexion", SlotRole.Isolation)
    val vPull = findSlotByPattern(plan, "VerticalPull")
    val hPull = findSlotByPattern(plan, "HorizontalPull")
    val rearDelt = findSlotByPattern(plan, "ShoulderHorizontalAbduction", SlotRole.Isolation)

    transferToReachMinimum(bicep, vPull, ANCHOR_MIN_SETS, MIN_ARM_ISO_ON_SPLIT_DAY)
    transferToReachMinimum(bicep, hPull, SECONDARY_COMPOUND_MIN_SETS, MIN_ARM_ISO_ON_SPLIT_DAY)
    transferToReachMinimum(bicep, rearDelt, SECONDARY_COMPOUND_MIN_SETS, MIN_ARM_ISO_ON_SPLIT_DAY)

    capDirectArmIsolation(plan, "Bicep", "ElbowFlexion")

    val cap = sessionHardSetCap(context)
    if (cap != null) {
        while (sessionTotalSets(plan) > cap) {
            val drop = slotToDropForSessionTimeCap(plan, context)
            if (drop == null || drop.setCount <= 0) break
            drop.setCount -= 1
        }
    }
}

private fun rebalanceCombinedUpperArmSpec(plan: SessionSlotPlan, context: ProgramGenerationContext) {
    val bicepSpec = (context.specialization["Bicep"] ?: "Maintenance") == "Specialize"
    val tricepSpec = (context.specialization["Tricep"] ?: "Maintenance") == "Specialize"
    if (!bicepSpec && !tricepSpec) return

    val bicep = findSlotByPattern(plan, "ElbowFlexion", SlotRole.Isolation)
    val tricep = findSlotByPattern(plan, "ElbowExtension", SlotRole.Isolation)
    val vPull = findSlotByPattern(plan, "VerticalPull")
    val hPull = findSlotByPattern(plan, "HorizontalPull")
    val hPress = findSlotByPattern(plan, "HorizontalPress")

    val armDonorFloor = MIN_ARM_ISO_ON_SPLIT_DAY
    if (bicepSpec && tricepSpec) {
        ensureUpperDayPullMinimum(plan)
        transferToReachMinimum(bicep, vPull, SECONDARY_COMPOUND_MIN_SETS, armDonorFloor)
        transferToReachMinimum(tricep, hPress, SECONDARY_COMPOUND_MIN_SETS, armDonorFloor)
        transferToReachMinimum(bicep, hPull, SECONDARY_COMPOUND_MIN_SETS, armDonorFloor)
        capDirectArmIsolation(plan, "Bicep", "ElbowFlexion")
        capDirectArmIsolation(plan, "Tricep", "ElbowExtension")
        balanceDualArmSpecIsolationInSession(bicep, tricep, plan)
    } else {
        if (bicepSpec) {
            transferToReachMinimum(bicep, vPull, SECONDARY_COMPOUND_MIN_SETS, armDonorFloor)
            transferToReachMinimum(bicep, hPull, SECONDARY_COMPOUND_MIN_SETS, armDonorFloor)
            capDirectArmIsolation(plan, "Bicep", "ElbowFlexion")
        }
        if (tricepSpec) {
            transferToReachMinimum(tricep, hPress, ANCHOR_MIN_SETS, armDonorFloor)
            capDirectArmIsolation(plan, "Tricep", "ElbowExtension")
        }
        ensureUpperDayPullMinimum(plan)
    }
}

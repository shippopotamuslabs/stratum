package com.stratum.domain.program.generation.split

import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.reference.minimumRecoveryCalendarGap

/** One muscle's fractional hard-set dose on a specific calendar weekday index. */
data class MuscleSessionExposure(
    val muscle: String,
    val weekdayIndex: Int,
    val fractionalSets: Double,
    val workoutName: String? = null,
    val sessionType: SessionTypeId? = null,
)

/**
 * Estimated per-muscle dose for a session type when building split templates
 * (no assembled lifts yet). Includes cross-domain touch slots.
 */
internal fun estimatedMuscleDosesForSessionType(
    sessionType: SessionTypeId,
    context: ProgramGenerationContext,
    siblingIndex: Int = 0,
    sessionPosition: Int? = null,
    composition: SessionComposition? = null,
): Map<String, Double> {
    val doses = mutableMapOf<String, Double>()
    for (slot in slotsForSessionType(sessionType, context, siblingIndex, sessionPosition, composition)) {
        val dose = estimatedDoseForSlot(slot, context, composition)
        doses[slot.primaryMuscle] = maxOf(doses[slot.primaryMuscle] ?: 0.0, dose)
    }
    return doses
}

private fun estimatedDoseForSlot(
    slot: WorkoutSlotTemplate,
    context: ProgramGenerationContext,
    composition: SessionComposition? = null,
): Double {
    if (slot.isCrossDomainTouch) return 2.0
    val level = context.specialization[slot.primaryMuscle] ?: "Maintenance"
    return when {
        composition == SessionComposition.UpperSpecFocused && level == "Specialize" -> 10.0
        level == "Specialize" -> 8.0
        level == "MinimumVolume" -> 3.0
        else -> 5.0
    }
}

/**
 * Scores how well [exposures] are spaced on the calendar. Higher is better (max 100).
 */
internal fun calendarRecoveryScoreFromExposures(
    exposures: List<MuscleSessionExposure>,
): Int {
    val summary = recoveryScheduleSummary(exposures)
    return (100 - summary.totalPenalty).coerceAtLeast(0)
}
data class RecoveryScheduleSummary(
    val violationCount: Int,
    val priorityViolationCount: Int,
    val priorityViolationsByMuscle: Map<String, Int>,
    val maxPriorityConsecutiveExposureDays: Int,
    val totalPenalty: Int,
    val severityScore: Int,
    val prioritySeverityScore: Int,
    val upperPullAdjacentLowerCount: Int = 0,
    val fullBodyLowerAdjacentCount: Int = 0,
    val fullBodyFullBodyAdjacentCount: Int = 0,
)

internal fun recoveryScheduleSummary(
    exposures: List<MuscleSessionExposure>,
    priorityMuscles: Set<String> = emptySet(),
): RecoveryScheduleSummary {
    if (exposures.isEmpty()) {
        return RecoveryScheduleSummary(0, 0, emptyMap(), 0, 0, 0, 0, 0, 0, 0)
    }
    val byMuscle = exposures.groupBy { it.muscle }
    var violationCount = 0
    var priorityViolationCount = 0
    val priorityViolationsByMuscle = mutableMapOf<String, Int>()
    var totalPenalty = 0
    var severityScore = 0
    var prioritySeverityScore = 0
    for ((muscle, sessions) in byMuscle) {
        if (sessions.size < 2) continue
        val isPriority = muscle in priorityMuscles
        val sorted = sessions.sortedBy { it.weekdayIndex }
        for (index in 1 until sorted.size) {
            val result = recoveryGapPenaltyResult(sorted[index - 1], sorted[index])
            if (result.penalty > 0) {
                violationCount++
                if (isPriority) {
                    priorityViolationCount++
                    priorityViolationsByMuscle[muscle] = (priorityViolationsByMuscle[muscle] ?: 0) + 1
                }
                totalPenalty += result.penalty
                severityScore += result.severity
                if (isPriority) prioritySeverityScore += result.severity
            }
        }
        val first = sorted.first()
        val last = sorted.last()
        val wrapGap = 7 - last.weekdayIndex + first.weekdayIndex
        if (wrapGap > 0) {
            val result = recoveryGapPenaltyResult(
                earlier = last,
                later = first.copy(weekdayIndex = first.weekdayIndex + 7),
                calendarGap = wrapGap,
            )
            if (result.penalty > 0) {
                violationCount++
                if (isPriority) {
                    priorityViolationCount++
                    priorityViolationsByMuscle[muscle] = (priorityViolationsByMuscle[muscle] ?: 0) + 1
                }
                totalPenalty += result.penalty
                severityScore += result.severity
                if (isPriority) prioritySeverityScore += result.severity
            }
        }
    }
    val pullLowerAdjacency = upperPullAdjacentLowerCount(exposures)
    val fullBodyLowerAdjacency = fullBodyLowerAdjacentCount(exposures)
    val fullBodyFullBodyAdjacency = fullBodyFullBodyAdjacentCount(exposures)
    return RecoveryScheduleSummary(
        violationCount = violationCount,
        priorityViolationCount = priorityViolationCount,
        priorityViolationsByMuscle = priorityViolationsByMuscle,
        maxPriorityConsecutiveExposureDays =
            maxPriorityConsecutiveExposureDays(exposures, priorityMuscles),
        totalPenalty =
            totalPenalty +
                pullLowerAdjacency * UPPER_PULL_LOWER_ADJACENCY_PENALTY +
                fullBodyLowerAdjacency * FULL_BODY_LOWER_ADJACENCY_PENALTY +
                fullBodyFullBodyAdjacency * FULL_BODY_FULL_BODY_ADJACENCY_PENALTY,
        severityScore = severityScore,
        prioritySeverityScore = prioritySeverityScore,
        upperPullAdjacentLowerCount = pullLowerAdjacency,
        fullBodyLowerAdjacentCount = fullBodyLowerAdjacency,
        fullBodyFullBodyAdjacentCount = fullBodyFullBodyAdjacency,
    )
}

/**
 * Count calendar-adjacent upper-pull / lower pairings (both directions).
 */
/** Penalty weight per upper-pull / lower calendar adjacency. */
private const val UPPER_PULL_LOWER_ADJACENCY_PENALTY = 60
private const val FULL_BODY_LOWER_ADJACENCY_PENALTY = 250
private const val FULL_BODY_FULL_BODY_ADJACENCY_PENALTY = 120

internal fun sessionTypesByWeekdayIndex(
    exposures: List<MuscleSessionExposure>,
): Map<Int, Set<SessionTypeId>> =
    exposures
        .filter { exposure -> exposure.sessionType != null }
        .distinctBy { exposure -> exposure.weekdayIndex to exposure.workoutName }
        .groupBy { exposure -> exposure.weekdayIndex }
        .mapValues { (_, dayExposures) -> dayExposures.mapNotNull { it.sessionType }.toSet() }

internal fun countAdjacentSessionTypePairs(
    exposures: List<MuscleSessionExposure>,
    predicate: (today: Set<SessionTypeId>, tomorrow: Set<SessionTypeId>) -> Boolean,
): Int {
    val sessionTypesByDay = sessionTypesByWeekdayIndex(exposures)
    var count = 0
    for (dayIndex in sessionTypesByDay.keys) {
        val today = sessionTypesByDay[dayIndex].orEmpty()
        val tomorrow = sessionTypesByDay[dayIndex + 1].orEmpty()
        if (tomorrow.isEmpty()) continue
        if (predicate(today, tomorrow)) {
            count++
        }
    }
    return count
}

internal fun upperPullAdjacentLowerCount(exposures: List<MuscleSessionExposure>): Int =
    countAdjacentSessionTypePairs(exposures) { today, tomorrow ->
        (SessionTypeId.UpperPull in today && SessionTypeId.Lower in tomorrow) ||
            (SessionTypeId.Lower in today && SessionTypeId.UpperPull in tomorrow)
    }

internal fun fullBodyLowerAdjacentCount(exposures: List<MuscleSessionExposure>): Int =
    countAdjacentSessionTypePairs(exposures) { today, tomorrow ->
        (SessionTypeId.FullBody in today && SessionTypeId.Lower in tomorrow) ||
            (SessionTypeId.Lower in today && SessionTypeId.FullBody in tomorrow)
    }

internal fun fullBodyFullBodyAdjacentCount(exposures: List<MuscleSessionExposure>): Int =
    countAdjacentSessionTypePairs(exposures) { today, tomorrow ->
        SessionTypeId.FullBody in today && SessionTypeId.FullBody in tomorrow
    }

/** @deprecated Use [upperPullAdjacentLowerCount]; retained for penalty weighting. */
internal fun upperPullAdjacentLowerPenalty(exposures: List<MuscleSessionExposure>): Int =
    upperPullAdjacentLowerCount(exposures) * UPPER_PULL_LOWER_ADJACENCY_PENALTY

internal fun maxPriorityConsecutiveExposureDays(
    exposures: List<MuscleSessionExposure>,
    priorityMuscles: Set<String>,
): Int =
    priorityMuscles.maxOfOrNull { muscle ->
        maxConsecutiveExposureDays(exposures, muscle)
    } ?: 0

internal fun maxConsecutiveExposureDays(
    exposures: List<MuscleSessionExposure>,
    muscle: String,
): Int {
    val days =
        exposures
            .filter { exposure -> exposure.muscle == muscle }
            .map { exposure -> exposure.weekdayIndex }
            .distinct()
            .sorted()
    return longestConsecutiveRun(days)
}

private fun longestConsecutiveRun(sortedDays: List<Int>): Int {
    if (sortedDays.isEmpty()) return 0
    var best = 1
    var current = 1
    for (index in 1 until sortedDays.size) {
        if (sortedDays[index] == sortedDays[index - 1] + 1) {
            current++
        } else {
            best = maxOf(best, current)
            current = 1
        }
    }
    return maxOf(best, current)
}

private data class GapPenaltyResult(
    val penalty: Int,
    val severity: Int,
)

private fun recoveryGapPenaltyResult(
    earlier: MuscleSessionExposure,
    later: MuscleSessionExposure,
    calendarGap: Int = later.weekdayIndex - earlier.weekdayIndex,
): GapPenaltyResult {
    val required = minimumRecoveryCalendarGap(
        muscle = earlier.muscle,
        earlierFractionalSets = earlier.fractionalSets,
        laterFractionalSets = later.fractionalSets,
    )
    if (required <= 0 || calendarGap >= required) return GapPenaltyResult(0, 0)
    val shortfall = required - calendarGap
    return GapPenaltyResult(
        penalty = recoveryGapPenalty(earlier, later, shortfall),
        severity = recoveryViolationSeverity(earlier, later, shortfall),
    )
}

private fun recoveryGapPenalty(
    earlier: MuscleSessionExposure,
    later: MuscleSessionExposure,
    shortfall: Int,
): Int {
    val doseWeight = maxOf(earlier.fractionalSets, later.fractionalSets).coerceAtLeast(1.0)
    return (shortfall * shortfall * doseWeight * 8.0).toInt()
}

private fun recoveryViolationSeverity(
    earlier: MuscleSessionExposure,
    later: MuscleSessionExposure,
    shortfall: Int,
): Int {
    val doseWeight = maxOf(earlier.fractionalSets, later.fractionalSets).coerceAtLeast(1.0)
    val multiplier = crossDomainTouchSeverityMultiplier(earlier, later)
    return (shortfall * doseWeight * doseWeight * multiplier).toInt()
}

private fun crossDomainTouchSeverityMultiplier(
    earlier: MuscleSessionExposure,
    later: MuscleSessionExposure,
): Int {
    if (earlier.muscle != later.muscle) return 1
    if (earlier.muscle !in CROSS_DOMAIN_TOUCH_MUSCLES) return 1
    val earlierLower =
        earlier.sessionType == SessionTypeId.Lower ||
            earlier.workoutName?.startsWith("Lower", ignoreCase = true) == true
    val laterUpperPush =
        later.sessionType == SessionTypeId.UpperPush ||
            later.workoutName?.startsWith("Upper Push", ignoreCase = true) == true ||
            (
                later.workoutName?.startsWith("Upper", ignoreCase = true) == true &&
                    later.workoutName.startsWith("Upper Pull", ignoreCase = true).not()
                )
    return if (earlierLower && laterUpperPush) 12 else 1
}

private val CROSS_DOMAIN_TOUCH_MUSCLES = setOf("Tricep", "Bicep", "Calf")

internal fun exposuresFromSessionTypes(
    sessionTypes: List<SessionTypeId>,
    weekdayIndices: List<Int>,
    context: ProgramGenerationContext?,
): List<MuscleSessionExposure> {
    if (context == null) {
        return legacyExposuresFromSessionPrimaries(sessionTypes, weekdayIndices)
    }
    val out = mutableListOf<MuscleSessionExposure>()
    sessionTypes.forEachIndexed { sessionIdx, sessionType ->
        val weekdayIdx = weekdayIndices.getOrElse(sessionIdx) { sessionIdx }
        val siblingIndex = siblingIndexForSessionType(sessionTypes, sessionIdx)
        for ((muscle, dose) in estimatedMuscleDosesForSessionType(sessionType, context, siblingIndex, sessionIdx)) {
            out.add(
                MuscleSessionExposure(
                    muscle = muscle,
                    weekdayIndex = weekdayIdx,
                    fractionalSets = dose,
                    sessionType = sessionType,
                ),
            )
        }
    }
    return out
}

/** Fallback when generation context is unavailable (unit tests). */
private fun legacyExposuresFromSessionPrimaries(
    sessionTypes: List<SessionTypeId>,
    weekdayIndices: List<Int>,
): List<MuscleSessionExposure> {
    val out = mutableListOf<MuscleSessionExposure>()
    sessionTypes.forEachIndexed { sessionIdx, sessionType ->
        val weekdayIdx = weekdayIndices.getOrElse(sessionIdx) { sessionIdx }
        for (muscle in primaryMusclesForSessionType(sessionType)) {
            out.add(MuscleSessionExposure(muscle, weekdayIdx, 5.0))
        }
    }
    return out
}

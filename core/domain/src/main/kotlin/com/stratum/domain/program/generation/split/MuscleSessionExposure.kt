package com.stratum.domain.program.generation.split

/** One muscle's fractional hard-set dose on a specific calendar weekday index. */
data class MuscleSessionExposure(
    val muscle: String,
    val weekdayIndex: Int,
    val fractionalSets: Double,
    val workoutName: String? = null,
    val sessionType: SessionTypeId? = null,
)

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

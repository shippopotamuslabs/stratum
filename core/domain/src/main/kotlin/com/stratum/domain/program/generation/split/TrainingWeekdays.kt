package com.stratum.domain.program.generation.split

private val WEEKDAY_ORDER =
    listOf(
        "Monday",
        "Tuesday",
        "Wednesday",
        "Thursday",
        "Friday",
        "Saturday",
        "Sunday",
    )

/** Mon=0 … Sun=6 indices for the user's training weekdays in calendar order. */
fun trainingWeekdayIndices(weekdays: List<String>): List<Int> =
    weekdays
        .map { weekday -> WEEKDAY_ORDER.indexOf(weekday) }
        .filter { index -> index >= 0 }
        .distinct()
        .sorted()

/**
 * Calendar-adjacent training day index pairs (gap of one day), including week wrap
 * (e.g. Sun=6 → Mon=0) when the user's layout trains on consecutive week-boundary days.
 */
fun calendarAdjacentTrainingDayPairs(trainingDayIndices: List<Int>): List<Pair<Int, Int>> {
    val sorted = trainingDayIndices.sorted()
    val pairs = mutableListOf<Pair<Int, Int>>()
    for (index in 0 until sorted.lastIndex) {
        val d1 = sorted[index]
        val d2 = sorted[index + 1]
        if (d2 - d1 == 1) {
            pairs += d1 to d2
        }
    }
    if (sorted.size >= 2) {
        val wrapEarlier = sorted.last()
        val wrapLater = sorted.first()
        if (7 - wrapEarlier + wrapLater == 1) {
            pairs += wrapEarlier to wrapLater
        }
    }
    return pairs
}

/** Preserves the user's declared training-day sequence (e.g. Sun → Mon → Wed with Tue rest). */
fun trainingWeekdayIndicesInUserOrder(weekdays: List<String>): List<Int> =
    weekdays.map { weekday -> com.stratum.domain.onboarding.Weekday.valueOf(weekday).ordinal }

internal fun sortedWeekdayIndices(weekdays: List<String>): List<Int> = trainingWeekdayIndices(weekdays)

internal fun sortedWeekdayIndicesFromWeekdays(weekdays: List<com.stratum.domain.onboarding.Weekday>): List<Int> =
    sortedWeekdayIndices(weekdays.map { weekday -> weekday.name })

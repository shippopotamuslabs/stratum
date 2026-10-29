package com.stratum.domain.program.generation.split

import com.stratum.domain.program.schedule.TrainingTimeline
import com.stratum.domain.onboarding.Weekday

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
 * Adjacent training slot pairs (gap of one day), including cycle wrap.
 *
 * Prefer [TrainingTimeline.adjacentTrainingPairs] when cycle-space is known.
 * This overload assumes a classic 7-day ring when [cycleLength] is omitted.
 */
fun calendarAdjacentTrainingDayPairs(
    trainingDayIndices: List<Int>,
    cycleLength: Int = 7,
): List<Pair<Int, Int>> =
    TrainingTimeline(
        cycleLength = cycleLength,
        trainingSlotIndices = trainingDayIndices.sorted(),
    ).adjacentTrainingPairs()

/** Preserves the user's declared training-day sequence (e.g. Sun → Mon → Wed with Tue rest). */
fun trainingWeekdayIndicesInUserOrder(weekdays: List<String>): List<Int> =
    weekdays.map { weekday -> com.stratum.domain.onboarding.Weekday.valueOf(weekday).ordinal }

internal fun sortedWeekdayIndices(weekdays: List<String>): List<Int> = trainingWeekdayIndices(weekdays)

internal fun sortedWeekdayIndicesFromWeekdays(weekdays: List<com.stratum.domain.onboarding.Weekday>): List<Int> =
    sortedWeekdayIndices(weekdays.map { weekday -> weekday.name })

package com.stratum.domain.program.generation.structure

import com.stratum.domain.program.generation.split.trainingWeekdayIndices
import com.stratum.domain.program.schedule.TrainingTimeline

/**
 * Which [SessionRegionFilter] values are worth searching for each training slot.
 *
 * Sessions are ordered by cycle slot via CP-SAT symmetry break (position i → sorted
 * training slot i), so [regionFiltersBySessionPosition] is indexed by session position.
 *
 * Policy (per training slot):
 * - **Rest-sandwiched** (rest on both cycle sides, ring wrap): [SessionRegionFilter.Unrestricted]
 *   only — pure UpperOnly/LowerOnly inflate the search tree and are almost never chosen when
 *   mixed activation is viable.
 * - **Not rest-sandwiched**: Unrestricted **plus** UpperOnly/LowerOnly.
 *   Unrestricted is required for the emergent mix-full families (Chest/Quads ↔ Back/Hams).
 *   True “everything on one day” full body remains rare under recovery hard-forbids without
 *   a rest sandwich; we do **not** ban Unrestricted on dense days (that forced pure U/L and
 *   broke specialization volume on 4–5 day sequential layouts).
 */
fun regionFiltersBySessionPosition(timeline: TrainingTimeline): List<Set<SessionRegionFilter>> {
    if (timeline.trainingSlotIndices.isEmpty()) return emptyList()
    return timeline.trainingSlotIndices.map { slot ->
        if (timeline.isRestSandwiched(slot)) {
            setOf(SessionRegionFilter.Unrestricted)
        } else {
            setOf(
                SessionRegionFilter.Unrestricted,
                SessionRegionFilter.UpperOnly,
                SessionRegionFilter.LowerOnly,
            )
        }
    }
}

/**
 * Classic weekday-list entry: builds a 7-day timeline from named weekdays.
 * Prefer [regionFiltersBySessionPosition] with a resolved [TrainingTimeline] for gen.
 */
fun regionFiltersBySessionPosition(weekdays: List<String>): List<Set<SessionRegionFilter>> {
    val trainingDays = trainingWeekdayIndices(weekdays)
    if (trainingDays.isEmpty()) return emptyList()
    return regionFiltersBySessionPosition(
        TrainingTimeline(cycleLength = 7, trainingSlotIndices = trainingDays),
    )
}

/**
 * Union of allowed region filters across the training calendar (for diagnostics / logging).
 * Solver constraints must use [regionFiltersBySessionPosition] (per-session domains).
 */
fun allowedRegionFiltersForCalendar(weekdays: List<String>): Set<SessionRegionFilter> =
    regionFiltersBySessionPosition(weekdays).flatten().toSet().ifEmpty {
        setOf(SessionRegionFilter.Unrestricted)
    }

fun allowedRegionFiltersForCalendar(timeline: TrainingTimeline): Set<SessionRegionFilter> =
    regionFiltersBySessionPosition(timeline).flatten().toSet().ifEmpty {
        setOf(SessionRegionFilter.Unrestricted)
    }

/**
 * True when [dayIndex] is a training slot with rest on the previous and next cycle slots.
 *
 * @param cycleLength full ring length (default 7 for classic Mon–Sun)
 */
fun isRestSandwichedTrainingDay(
    dayIndex: Int,
    trainingDays: Set<Int>,
    cycleLength: Int = 7,
): Boolean {
    if (dayIndex !in trainingDays || cycleLength <= 0) return false
    val previous = (dayIndex - 1 + cycleLength) % cycleLength
    val next = (dayIndex + 1) % cycleLength
    return previous !in trainingDays && next !in trainingDays
}

fun restSandwichedTrainingDays(weekdays: List<String>): Set<Int> {
    val trainingDays = trainingWeekdayIndices(weekdays).toSet()
    return trainingDays
        .filter { day -> isRestSandwichedTrainingDay(day, trainingDays, cycleLength = 7) }
        .toSet()
}

fun restSandwichedTrainingDays(timeline: TrainingTimeline): Set<Int> =
    timeline.trainingSlotIndices.filter { slot -> timeline.isRestSandwiched(slot) }.toSet()

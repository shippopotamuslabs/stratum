package com.stratum.domain.program.generation.structure

import com.stratum.domain.program.generation.split.trainingWeekdayIndices

/**
 * Which [SessionRegionFilter] values are worth searching for each training weekday.
 *
 * Sessions are ordered by calendar day via CP-SAT symmetry break (position i → sorted
 * training day i), so [regionFiltersBySessionPosition] is indexed by session position.
 *
 * Policy (per training weekday):
 * - **Rest-sandwiched** (rest on both calendar sides, week wrap): [SessionRegionFilter.Unrestricted]
 *   only — pure UpperOnly/LowerOnly inflate the search tree and are almost never chosen when
 *   mixed activation is viable.
 * - **Not rest-sandwiched**: Unrestricted **plus** UpperOnly/LowerOnly.
 *   Unrestricted is required for the emergent mix-full families (Chest/Quads ↔ Back/Hams).
 *   True “everything on one day” full body remains rare under recovery hard-forbids without
 *   a rest sandwich; we do **not** ban Unrestricted on dense days (that forced pure U/L and
 *   broke specialization volume on 4–5 day sequential layouts).
 */
fun regionFiltersBySessionPosition(weekdays: List<String>): List<Set<SessionRegionFilter>> {
    val trainingDays = trainingWeekdayIndices(weekdays)
    if (trainingDays.isEmpty()) return emptyList()
    val trainingSet = trainingDays.toSet()
    return trainingDays.map { day ->
        if (isRestSandwichedTrainingDay(day, trainingSet)) {
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
 * Union of allowed region filters across the training calendar (for diagnostics / logging).
 * Solver constraints must use [regionFiltersBySessionPosition] (per-session domains).
 */
fun allowedRegionFiltersForCalendar(weekdays: List<String>): Set<SessionRegionFilter> =
    regionFiltersBySessionPosition(weekdays).flatten().toSet().ifEmpty {
        setOf(SessionRegionFilter.Unrestricted)
    }

/**
 * True when [dayIndex] (Mon=0 … Sun=6) is a training day with rest on the previous and next
 * calendar days (week wrap: Sun↔Mon).
 */
fun isRestSandwichedTrainingDay(dayIndex: Int, trainingDays: Set<Int>): Boolean {
    if (dayIndex !in trainingDays) return false
    val previous = (dayIndex + 6) % 7
    val next = (dayIndex + 1) % 7
    return previous !in trainingDays && next !in trainingDays
}

fun restSandwichedTrainingDays(weekdays: List<String>): Set<Int> {
    val trainingDays = trainingWeekdayIndices(weekdays).toSet()
    return trainingDays.filter { day -> isRestSandwichedTrainingDay(day, trainingDays) }.toSet()
}

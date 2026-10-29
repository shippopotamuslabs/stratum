package com.stratum.domain.program.schedule

import com.stratum.domain.program.Program
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import com.stratum.domain.onboarding.Weekday

/**
 * Resolves the calendar anchor (epoch day of cycle slot 0) for projection labels.
 *
 * Classic generated weeks use a **Mon-start 7-slot** layout: slot index = [Weekday.ordinal]
 * (rests = complement of training weekdays). Anchoring slot 0 to "today" when today is not
 * Monday shifts every training day (e.g. M/T/Th/F/Sa becomes Tue-first with Monday as rest).
 *
 * - [Program.cycleLength] == 7 → Monday of the week containing [today] (ISO previous-or-same Monday)
 * - otherwise → existing [Program.cycleStartEpochDay] if set, else [today]
 */
fun resolveInitialCycleStartEpochDay(
    program: Program,
    today: LocalDate = LocalDate.now(),
): Long {
    if (program.cycleLength == FIXED_WEEK_CYCLE_LENGTH) {
        return mondayOfWeekContaining(today).toEpochDay()
    }
    return program.cycleStartEpochDay ?: today.toEpochDay()
}

/** Monday of the calendar week that contains [date] (Mon–Sun week, Mon first). */
fun mondayOfWeekContaining(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

private const val FIXED_WEEK_CYCLE_LENGTH = 7

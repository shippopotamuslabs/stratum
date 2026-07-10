package com.stratum.feature.history

import com.stratum.domain.training.HistoryDaySession
import com.stratum.domain.training.WorkoutHistoryCalendar
import com.stratum.feature.history.ui.HistoryCalendarMode
import com.stratum.feature.history.ui.HistoryDayUi
import com.stratum.feature.history.ui.HistoryMiniMonthUi
import com.stratum.feature.history.ui.HistoryMonthUi
import com.stratum.feature.history.ui.HistoryYearUi
import com.stratum.feature.history.ui.WorkoutHistoryUiState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Maps domain calendar read model → presentation. Month grid uses Sunday-first columns to match
 * the product mockups.
 *
 * Builds only the tree needed for [HistoryCalendarMode] so mode switches and large import
 * histories do not eagerly materialize both month day grids and year heatmaps every time.
 */
internal object WorkoutHistoryMapper {
    fun toUiState(
        calendar: WorkoutHistoryCalendar,
        mode: HistoryCalendarMode,
        today: LocalDate,
        scrollToMonth: YearMonth?,
        scrollToYear: Int? = null,
    ): WorkoutHistoryUiState.Loaded {
        val firstWorkout = calendar.days.keys.minOrNull()
        val startMonth = YearMonth.from(firstWorkout ?: today)
        val endMonth = YearMonth.from(today)
        val months =
            if (mode == HistoryCalendarMode.Month) {
                buildMonths(startMonth, endMonth, calendar.days)
            } else {
                emptyList()
            }
        val years =
            if (mode == HistoryCalendarMode.Month) {
                emptyList()
            } else {
                buildYears(startMonth, endMonth, calendar.days)
            }
        return WorkoutHistoryUiState.Loaded(
            mode = mode,
            weekStreak = calendar.weekStreak,
            restDaysCount = calendar.restDaysCount,
            months = months,
            years = years,
            scrollToMonth = scrollToMonth,
            scrollToYear = scrollToYear,
        )
    }

    private fun buildMonths(
        startMonth: YearMonth,
        endMonth: YearMonth,
        days: Map<LocalDate, List<HistoryDaySession>>,
    ): List<HistoryMonthUi> {
        val result = mutableListOf<HistoryMonthUi>()
        var cursor = endMonth
        while (!cursor.isBefore(startMonth)) {
            result += monthUi(cursor, days)
            cursor = cursor.minusMonths(1)
        }
        return result
    }

    private fun buildYears(
        startMonth: YearMonth,
        endMonth: YearMonth,
        days: Map<LocalDate, List<HistoryDaySession>>,
    ): List<HistoryYearUi> {
        val startYear = startMonth.year
        val endYear = endMonth.year
        return (endYear downTo startYear).map { year ->
            val months =
                (1..12).map { month ->
                    val ym = YearMonth.of(year, month)
                    miniMonthUi(
                        yearMonth = ym,
                        days = days,
                        isNavigable = !ym.isBefore(startMonth) && !ym.isAfter(endMonth),
                    )
                }
            HistoryYearUi(year = year, months = months)
        }
    }

    private fun monthUi(
        yearMonth: YearMonth,
        days: Map<LocalDate, List<HistoryDaySession>>,
    ): HistoryMonthUi {
        val length = yearMonth.lengthOfMonth()
        val leading = sundayFirstIndex(yearMonth.atDay(1).dayOfWeek)
        val dayUis =
            (1..length).map { day ->
                val date = yearMonth.atDay(day)
                val sessions = days[date].orEmpty()
                val primary = sessions.firstOrNull()
                HistoryDayUi(
                    date = date,
                    dayOfMonth = day,
                    hasWorkout = primary != null,
                    // Domain already stores the workout title (not a lift name).
                    workoutLabel = primary?.workoutName,
                    sessionId = primary?.sessionId?.value,
                )
            }
        return HistoryMonthUi(
            yearMonth = yearMonth,
            leadingEmptyDays = leading,
            days = dayUis,
        )
    }

    private fun miniMonthUi(
        yearMonth: YearMonth,
        days: Map<LocalDate, List<HistoryDaySession>>,
        isNavigable: Boolean,
    ): HistoryMiniMonthUi {
        val length = yearMonth.lengthOfMonth()
        val leading = sundayFirstIndex(yearMonth.atDay(1).dayOfWeek)
        val workoutDays =
            (1..length)
                .filter { day -> days.containsKey(yearMonth.atDay(day)) }
                .toSet()
        return HistoryMiniMonthUi(
            yearMonth = yearMonth,
            leadingEmptyDays = leading,
            dayCount = length,
            workoutDays = workoutDays,
            isNavigable = isNavigable,
        )
    }

    /** Sunday = 0 … Saturday = 6 (ISO Monday=1 … Sunday=7 → % 7). */
    private fun sundayFirstIndex(dayOfWeek: DayOfWeek): Int = dayOfWeek.value % 7
}

package com.stratum.feature.history.ui

import java.time.LocalDate
import java.time.YearMonth

enum class HistoryCalendarMode {
    Month,
    Year,
    MultiYear,
}

/**
 * Presentation state for the workout history calendar. Domain models never reach composables.
 */
sealed interface WorkoutHistoryUiState {
    data object Loading : WorkoutHistoryUiState

    data object Error : WorkoutHistoryUiState

    data class Loaded(
        val mode: HistoryCalendarMode,
        val weekStreak: Int,
        val restDaysCount: Int,
        /** Newest month first for LazyColumn Month mode. */
        val months: List<HistoryMonthUi>,
        /** Years newest first for Year / Multi-year modes. */
        val years: List<HistoryYearUi>,
        /** When non-null, Month mode should scroll this [YearMonth] into view then clear. */
        val scrollToMonth: YearMonth? = null,
        /** When non-null, Year mode should scroll this year into view then clear. */
        val scrollToYear: Int? = null,
    ) : WorkoutHistoryUiState
}

data class HistoryMonthUi(
    val yearMonth: YearMonth,
    /** Leading empty cells before day 1 (0 = Sunday … 6 = Saturday). */
    val leadingEmptyDays: Int,
    val days: List<HistoryDayUi>,
)

data class HistoryDayUi(
    val date: LocalDate,
    val dayOfMonth: Int,
    val hasWorkout: Boolean,
    /** Primary label under the day circle (newest session; ellipsis applied in UI). */
    val workoutLabel: String?,
    /**
     * Completed session ids for this day, newest first.
     * Single id → open detail; multiple → day session list then detail.
     */
    val sessionIds: List<String>,
)

data class HistoryYearUi(
    val year: Int,
    val months: List<HistoryMiniMonthUi>,
)

data class HistoryMiniMonthUi(
    val yearMonth: YearMonth,
    val leadingEmptyDays: Int,
    val dayCount: Int,
    /** Day-of-month values (1-based) that have workouts. */
    val workoutDays: Set<Int>,
    /**
     * When true, Year-mode tap navigates to Month mode for this [yearMonth]
     * (only months present in the Month LazyList range).
     */
    val isNavigable: Boolean = true,
)

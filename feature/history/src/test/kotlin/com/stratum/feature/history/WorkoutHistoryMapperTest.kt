package com.stratum.feature.history

import com.stratum.domain.training.HistoryDaySession
import com.stratum.domain.training.WorkoutHistoryCalendar
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.feature.history.ui.HistoryCalendarMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class WorkoutHistoryMapperTest {

    @Test
    @DisplayName("Months are newest-first and include workout labels")
    fun monthsNewestFirstWithLabels() {
        val calendar =
            WorkoutHistoryCalendar(
                days =
                    mapOf(
                        LocalDate.of(2025, 11, 2) to
                            listOf(
                                HistoryDaySession(
                                    sessionId = TrainingSessionId("ts-1"),
                                    workoutName = "Upper A",
                                    completedAt = Instant.parse("2025-11-02T12:00:00Z"),
                                ),
                            ),
                    ),
                weekStreak = 1,
                restDaysCount = 0,
            )
        val loaded =
            WorkoutHistoryMapper.toUiState(
                calendar = calendar,
                mode = HistoryCalendarMode.Month,
                today = LocalDate.of(2025, 12, 1),
                scrollToMonth = null,
            )
        assertEquals(YearMonth.of(2025, 12), loaded.months.first().yearMonth)
        assertEquals(YearMonth.of(2025, 11), loaded.months.last().yearMonth)
        val novDay =
            loaded.months
                .first { it.yearMonth == YearMonth.of(2025, 11) }
                .days
                .first { it.dayOfMonth == 2 }
        assertTrue(novDay.hasWorkout)
        assertEquals("Upper A", novDay.workoutLabel)
        assertEquals(listOf("ts-1"), novDay.sessionIds)
    }

    @Test
    @DisplayName("Multiple sessions on one day map all ids newest-first")
    fun multiSessionDayMapsAllIds() {
        val calendar =
            WorkoutHistoryCalendar(
                days =
                    mapOf(
                        LocalDate.of(2025, 11, 2) to
                            listOf(
                                HistoryDaySession(
                                    sessionId = TrainingSessionId("ts-new"),
                                    workoutName = "Upper A",
                                    completedAt = Instant.parse("2025-11-02T18:00:00Z"),
                                ),
                                HistoryDaySession(
                                    sessionId = TrainingSessionId("ts-old"),
                                    workoutName = "Lower B",
                                    completedAt = Instant.parse("2025-11-02T10:00:00Z"),
                                ),
                            ),
                    ),
                weekStreak = 1,
                restDaysCount = 0,
            )
        val loaded =
            WorkoutHistoryMapper.toUiState(
                calendar = calendar,
                mode = HistoryCalendarMode.Month,
                today = LocalDate.of(2025, 12, 1),
                scrollToMonth = null,
            )
        val novDay =
            loaded.months
                .first { it.yearMonth == YearMonth.of(2025, 11) }
                .days
                .first { it.dayOfMonth == 2 }
        assertTrue(novDay.hasWorkout)
        assertEquals("Upper A", novDay.workoutLabel)
        assertEquals(listOf("ts-new", "ts-old"), novDay.sessionIds)
    }

    @Test
    @DisplayName("Empty history still renders current month")
    fun emptyHistoryRendersCurrentMonth() {
        val loaded =
            WorkoutHistoryMapper.toUiState(
                calendar =
                    WorkoutHistoryCalendar(
                        days = emptyMap(),
                        weekStreak = 0,
                        restDaysCount = 0,
                    ),
                mode = HistoryCalendarMode.Month,
                today = LocalDate.of(2025, 7, 9),
                scrollToMonth = null,
            )
        assertEquals(listOf(YearMonth.of(2025, 7)), loaded.months.map { it.yearMonth })
    }

    @Test
    @DisplayName("scrollToYear is passed through for Multi-year → Year drill-down")
    fun scrollToYearPassedThrough() {
        val loaded =
            WorkoutHistoryMapper.toUiState(
                calendar =
                    WorkoutHistoryCalendar(
                        days = emptyMap(),
                        weekStreak = 0,
                        restDaysCount = 0,
                    ),
                mode = HistoryCalendarMode.Year,
                today = LocalDate.of(2025, 7, 9),
                scrollToMonth = null,
                scrollToYear = 2025,
            )
        assertEquals(2025, loaded.scrollToYear)
        assertEquals(HistoryCalendarMode.Year, loaded.mode)
    }

    @Test
    @DisplayName("Month mode builds months only; Year mode builds years only")
    fun modeAwareTreeBuild() {
        val calendar =
            WorkoutHistoryCalendar(
                days =
                    mapOf(
                        LocalDate.of(2025, 6, 1) to
                            listOf(
                                HistoryDaySession(
                                    sessionId = TrainingSessionId("ts-1"),
                                    workoutName = "Upper A",
                                    completedAt = Instant.parse("2025-06-01T12:00:00Z"),
                                ),
                            ),
                    ),
                weekStreak = 1,
                restDaysCount = 0,
            )
        val monthMode =
            WorkoutHistoryMapper.toUiState(
                calendar = calendar,
                mode = HistoryCalendarMode.Month,
                today = LocalDate.of(2025, 7, 9),
                scrollToMonth = null,
            )
        assertTrue(monthMode.months.isNotEmpty())
        assertTrue(monthMode.years.isEmpty())

        val yearMode =
            WorkoutHistoryMapper.toUiState(
                calendar = calendar,
                mode = HistoryCalendarMode.Year,
                today = LocalDate.of(2025, 7, 9),
                scrollToMonth = null,
            )
        assertTrue(yearMode.months.isEmpty())
        assertTrue(yearMode.years.isNotEmpty())
    }
}

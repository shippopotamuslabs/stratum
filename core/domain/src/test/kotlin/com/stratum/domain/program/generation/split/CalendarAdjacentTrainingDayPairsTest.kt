package com.stratum.domain.program.generation.split

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CalendarAdjacentTrainingDayPairsTest {
    @Test
    fun mtwfsIncludesOnlyTrueCalendarNeighbors() {
        val weekdays = listOf("Monday", "Tuesday", "Wednesday", "Friday", "Saturday")
        val pairs = calendarAdjacentTrainingDayPairs(trainingWeekdayIndices(weekdays))
        assertEquals(listOf(0 to 1, 1 to 2, 4 to 5), pairs)
    }

    @Test
    fun sean5IncludesSundayMondayWrapAround() {
        val weekdays = listOf("Sunday", "Monday", "Wednesday", "Thursday", "Friday")
        val pairs = calendarAdjacentTrainingDayPairs(trainingWeekdayIndices(weekdays))
        assertEquals(listOf(2 to 3, 3 to 4, 6 to 0), pairs)
    }
}

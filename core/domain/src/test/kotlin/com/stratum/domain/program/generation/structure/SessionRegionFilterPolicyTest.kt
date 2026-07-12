package com.stratum.domain.program.generation.structure

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SessionRegionFilterPolicyTest {

    private val unrestrictedOnly = setOf(SessionRegionFilter.Unrestricted)
    private val allFilters =
        setOf(
            SessionRegionFilter.Unrestricted,
            SessionRegionFilter.UpperOnly,
            SessionRegionFilter.LowerOnly,
        )

    @Test
    @DisplayName("Mon/Wed/Fri — each day rest-sandwiched → Unrestricted only per session")
    fun monWedFriUnrestrictedOnly() {
        val weekdays = listOf("Monday", "Wednesday", "Friday")
        assertEquals(
            listOf(unrestrictedOnly, unrestrictedOnly, unrestrictedOnly),
            regionFiltersBySessionPosition(weekdays),
        )
        assertEquals(unrestrictedOnly, allowedRegionFiltersForCalendar(weekdays))
        assertTrue(restSandwichedTrainingDays(weekdays).containsAll(setOf(0, 2, 4)))
    }

    @Test
    @DisplayName("Mon–Fri sequential dense — Unrestricted + U/L (mix-full families stay viable)")
    fun sequentialDenseAllowsUnrestrictedAndUl() {
        val weekdays =
            listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")
        assertEquals(
            List(5) { allFilters },
            regionFiltersBySessionPosition(weekdays),
        )
        assertEquals(allFilters, allowedRegionFiltersForCalendar(weekdays))
        assertTrue(restSandwichedTrainingDays(weekdays).isEmpty())
    }

    @Test
    @DisplayName("sean5 Sun/Mon/Wed/Thu/Fri — no rest sandwich → Unrestricted + U/L")
    fun sean5AllowsUnrestrictedAndUl() {
        val weekdays = listOf("Sunday", "Monday", "Wednesday", "Thursday", "Friday")
        val filters = regionFiltersBySessionPosition(weekdays)
        assertEquals(5, filters.size)
        assertTrue(filters.all { it == allFilters })
        assertEquals(allFilters, allowedRegionFiltersForCalendar(weekdays))
    }

    @Test
    @DisplayName("mixed calendar — dense cluster keeps all filters; isolated Fri Unrestricted only")
    fun mixedCalendarPerSession() {
        // Mon+Tue adjacent (not sandwich), Fri isolated (sandwich)
        val weekdays = listOf("Monday", "Tuesday", "Friday")
        assertEquals(
            listOf(allFilters, allFilters, unrestrictedOnly),
            regionFiltersBySessionPosition(weekdays),
        )
        assertEquals(allFilters, allowedRegionFiltersForCalendar(weekdays))
        assertEquals(setOf(4), restSandwichedTrainingDays(weekdays))
    }

    @Test
    @DisplayName("Mon/Wed/Fri/Sat/Sun — only Wed rest-sandwiched → Unrestricted only there")
    fun partialSandwichCalendar() {
        val weekdays =
            listOf("Monday", "Wednesday", "Friday", "Saturday", "Sunday")
        assertEquals(
            listOf(
                allFilters, // Mon
                unrestrictedOnly, // Wed
                allFilters, // Fri
                allFilters, // Sat
                allFilters, // Sun
            ),
            regionFiltersBySessionPosition(weekdays),
        )
        assertEquals(setOf(2), restSandwichedTrainingDays(weekdays))
    }

    @Test
    fun isolatedDayIsRestSandwiched() {
        assertTrue(isRestSandwichedTrainingDay(2, setOf(2))) // Wednesday alone
        assertFalse(isRestSandwichedTrainingDay(0, setOf(0, 1))) // Mon with Tue
    }

    @Test
    @DisplayName("SessionRegionFilter ordinal freeze for CP-SAT")
    fun ordinalFreeze() {
        assertEquals(0, SessionRegionFilter.UpperOnly.ordinal)
        assertEquals(1, SessionRegionFilter.LowerOnly.ordinal)
        assertEquals(2, SessionRegionFilter.Unrestricted.ordinal)
    }
}

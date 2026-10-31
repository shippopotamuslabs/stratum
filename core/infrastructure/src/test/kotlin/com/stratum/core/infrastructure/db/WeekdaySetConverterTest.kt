package com.stratum.core.infrastructure.db

import com.stratum.domain.onboarding.Weekday
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class WeekdaySetConverterTest {

    private val converter = WeekdaySetConverter()

    @Test
    @DisplayName("Given an empty set when encoded then it round-trips to empty")
    fun emptySetRoundTrips() {
        val encoded = converter.fromWeekdaySet(emptySet())
        assertEquals("", encoded)
        assertTrue(converter.toWeekdaySet(encoded).isEmpty())
    }

    @Test
    @DisplayName("Given null or blank when decoded then it yields an empty set")
    fun nullOrBlankDecodesToEmpty() {
        assertTrue(converter.toWeekdaySet(null).isEmpty())
        assertTrue(converter.toWeekdaySet("").isEmpty())
        assertTrue(converter.toWeekdaySet("   ").isEmpty())
    }

    @Test
    @DisplayName("Given weekdays in different order when encoded then decode is order-independent")
    fun orderIndependentRoundTrip() {
        val setA = setOf(Weekday.Monday, Weekday.Wednesday, Weekday.Friday)
        val setB = setOf(Weekday.Friday, Weekday.Monday, Weekday.Wednesday)

        assertEquals(setA, converter.toWeekdaySet(converter.fromWeekdaySet(setA)))
        assertEquals(setA, converter.toWeekdaySet(converter.fromWeekdaySet(setB)))
    }

    @Test
    @DisplayName("Given unknown tokens when decoded then they are dropped")
    fun unknownTokensDropped() {
        val decoded = converter.toWeekdaySet("Monday,Funday,Friday")
        assertEquals(setOf(Weekday.Monday, Weekday.Friday), decoded)
    }
}

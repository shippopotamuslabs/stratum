package com.stratum.domain.response

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class DayBucketSupportTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val newYork: ZoneId = ZoneId.of("America/New_York")

    @Test
    @DisplayName("dayStartFor is idempotent for the same zone")
    fun dayStartIdempotent() {
        val instant = Instant.parse("2026-07-22T20:00:00-04:00")
        val once = DayBucketSupport.dayStartFor(instant, newYork)
        val twice = DayBucketSupport.dayStartFor(once, newYork)
        assertEquals(once, twice)
        assertEquals(LocalDate.of(2026, 7, 22), once.atZone(newYork).toLocalDate())
    }

    @Test
    @DisplayName("evening US local time before local midnight stays on local calendar day")
    fun eveningDoesNotFlipToUtcTomorrow() {
        // Wed 20:30 EDT = Thu 00:30 UTC
        val asOf = Instant.parse("2026-07-23T00:30:00Z")
        assertEquals(
            LocalDate.of(2026, 7, 22),
            DayBucketSupport.dayStartFor(asOf, newYork).atZone(newYork).toLocalDate(),
        )
        assertEquals(
            LocalDate.of(2026, 7, 23),
            DayBucketSupport.dayStartFor(asOf, utc).atZone(utc).toLocalDate(),
        )
    }

    @Test
    @DisplayName("zeroFillDaily ends on local day and spans seven LocalDates")
    fun zeroFillLocalSpan() {
        val asOf = Instant.parse("2026-07-23T00:30:00Z")
        val filled = DayBucketSupport.zeroFillDaily(
            points = emptyList(),
            asOf = asOf,
            dayCount = 7,
            zone = newYork,
        )
        assertEquals(7, filled.size)
        assertEquals(LocalDate.of(2026, 7, 16), filled.first().first.atZone(newYork).toLocalDate())
        assertEquals(LocalDate.of(2026, 7, 22), filled.last().first.atZone(newYork).toLocalDate())
    }

    @Test
    @DisplayName("zeroFillDaily sums multiple points that land on the same LocalDate")
    fun zeroFillSumsSameDay() {
        val asOf = Instant.parse("2025-06-12T18:00:00Z")
        val day = Instant.parse("2025-06-11T10:00:00Z")
        val filled = DayBucketSupport.zeroFillDaily(
            points = listOf(
                day to 2.0,
                day.plusSeconds(3600) to 1.5,
            ),
            asOf = asOf,
            dayCount = 7,
            zone = utc,
        )
        assertEquals(3.5, filled.single { it.second > 0.0 }.second, 0.001)
    }
}

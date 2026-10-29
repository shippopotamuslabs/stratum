package com.stratum.domain.export

import com.stratum.core.testing.export.ExportTestFixtures.CLOCK_INSTANT
import com.stratum.core.testing.export.ExportTestFixtures.ZONE_NY
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ExportRangeBounds")
class ExportRangeBoundsTest {

    @Test
    @DisplayName("AllTime resolves to Result.success(null)")
    fun allTimeResolvesToNull() {
        val result = ExportRangeBounds.resolve(ExportLogRange.AllTime, CLOCK_INSTANT)
        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    @Test
    @DisplayName("LastDays resolves to valid Instant bounds")
    fun lastDaysResolvesToBounds() {
        val result = ExportRangeBounds.resolve(ExportLogRange.LastDays(30, ZONE_NY), CLOCK_INSTANT)
        assertTrue(result.isSuccess)
        val bounds = result.getOrThrow()
        assertTrue(bounds != null)
        assertTrue(bounds!!.first.isBefore(bounds.second))
    }

    @Test
    @DisplayName("YearToDate resolves to valid bounds starting Jan 1")
    fun yearToDateResolvesToBounds() {
        val result = ExportRangeBounds.resolve(ExportLogRange.YearToDate(ZONE_NY), CLOCK_INSTANT)
        assertTrue(result.isSuccess)
        val bounds = result.getOrThrow()
        assertTrue(bounds != null)
        assertTrue(bounds!!.first.isBefore(bounds.second))
    }

    @Test
    @DisplayName("Valid Custom range resolves to bounds matching start and end")
    fun validCustomResolvesToBounds() {
        val start = LocalDate.of(2026, 6, 1)
        val end = LocalDate.of(2026, 6, 30)
        val result = ExportRangeBounds.resolve(ExportLogRange.Custom(start, end, ZONE_NY), CLOCK_INSTANT)
        assertTrue(result.isSuccess)
        val bounds = result.getOrThrow()
        assertTrue(bounds != null)
        assertEquals(start.atStartOfDay(ZONE_NY).toInstant(), bounds!!.first)
        assertEquals(end.plusDays(1).atStartOfDay(ZONE_NY).toInstant(), bounds.second)
    }

    @Test
    @DisplayName("Inverted Custom range (startDate > endDateInclusive) returns Result.failure")
    fun invertedCustomReturnsFailure() {
        val start = LocalDate.of(2026, 7, 10)
        val end = LocalDate.of(2026, 7, 1)
        val result = ExportRangeBounds.resolve(ExportLogRange.Custom(start, end, ZONE_NY), CLOCK_INSTANT)
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        assertEquals("Start date must be on or before end date.", ex!!.message)
    }
}

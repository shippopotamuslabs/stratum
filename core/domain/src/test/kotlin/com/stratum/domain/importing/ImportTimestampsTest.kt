package com.stratum.domain.importing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class ImportTimestampsTest {
    @Test
    fun `parseOrNull accepts ISO instant`() {
        assertEquals(
            Instant.parse("2025-12-28T15:30:00Z"),
            ImportTimestamps.parseOrNull("2025-12-28T15:30:00Z"),
        )
    }

    @Test
    fun `parseOrNull accepts date-only as UTC midnight`() {
        assertEquals(
            Instant.parse("2025-12-28T00:00:00Z"),
            ImportTimestamps.parseOrNull("2025-12-28"),
        )
    }

    @Test
    fun `parseOrNull accepts local datetime without zone`() {
        assertEquals(
            Instant.parse("2025-12-28T10:15:00Z"),
            ImportTimestamps.parseOrNull("2025-12-28T10:15:00"),
        )
    }

    @Test
    fun `parseOrNull accepts space separated local datetime`() {
        assertEquals(
            Instant.parse("2018-01-31T20:21:08Z"),
            ImportTimestamps.parseOrNull("2018-01-31 20:21:08"),
        )
    }

    @Test
    fun `parseOrNull accepts space separated local datetime with fractional seconds`() {
        assertEquals(
            Instant.parse("2018-01-31T20:21:08.123Z"),
            ImportTimestamps.parseOrNull("2018-01-31 20:21:08.123"),
        )
    }

    @Test
    fun `parseOrNull returns null for unsupported format without throwing`() {
        assertEquals(null, ImportTimestamps.parseOrNull("not-a-date"))
    }

    @Test
    fun `parseOrNull returns instant for valid timestamp`() {
        assertEquals(
            Instant.parse("2025-12-28T15:30:00Z"),
            ImportTimestamps.parseOrNull("2025-12-28T15:30:00Z"),
        )
    }
}

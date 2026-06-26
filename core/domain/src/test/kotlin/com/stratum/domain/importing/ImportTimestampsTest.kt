package com.stratum.domain.importing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class ImportTimestampsTest {
    @Test
    fun `parse accepts ISO instant`() {
        assertEquals(
            Instant.parse("2025-12-28T15:30:00Z"),
            ImportTimestamps.parse("2025-12-28T15:30:00Z"),
        )
    }

    @Test
    fun `parse accepts date-only as UTC midnight`() {
        assertEquals(
            Instant.parse("2025-12-28T00:00:00Z"),
            ImportTimestamps.parse("2025-12-28"),
        )
    }

    @Test
    fun `parse accepts local datetime without zone`() {
        assertEquals(
            Instant.parse("2025-12-28T10:15:00Z"),
            ImportTimestamps.parse("2025-12-28T10:15:00"),
        )
    }

    @Test
    fun `parse accepts space separated local datetime`() {
        assertEquals(
            Instant.parse("2018-01-31T20:21:08Z"),
            ImportTimestamps.parse("2018-01-31 20:21:08"),
        )
    }

    @Test
    fun `parse accepts space separated local datetime with fractional seconds`() {
        assertEquals(
            Instant.parse("2018-01-31T20:21:08.123Z"),
            ImportTimestamps.parse("2018-01-31 20:21:08.123"),
        )
    }

    @Test
    fun `parse rejects garbage`() {
        assertThrows<IllegalArgumentException> {
            ImportTimestamps.parse("not-a-date")
        }
    }
}


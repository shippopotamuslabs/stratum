package com.stratum.domain.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TelemetryPiiScrubberTest {
    @Test
    fun `scrub redacts email addresses`() {
        val scrubbed = TelemetryPiiScrubber.scrub("Contact user@example.com for help")

        assertEquals(
            "Contact ${TelemetryPiiScrubber.REDACTED_EMAIL} for help",
            scrubbed,
        )
    }

    @Test
    fun `scrub returns null for null input`() {
        assertNull(TelemetryPiiScrubber.scrub(null))
    }
}

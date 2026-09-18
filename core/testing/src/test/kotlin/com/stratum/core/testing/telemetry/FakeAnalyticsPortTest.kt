package com.stratum.core.testing.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class FakeAnalyticsPortTest {
    private lateinit var fake: FakeAnalyticsPort

    @BeforeEach
    fun setUp() {
        fake = FakeAnalyticsPort()
    }

    @Test
    @DisplayName("Given an event when logged then records name and params")
    fun recordsLoggedEvent() {
        fake.logEvent("flag_exposure", mapOf("flag_key" to "enable_sample_flag", "enabled" to "true"))

        val event = fake.events.single()
        assertEquals("flag_exposure", event.name)
        assertEquals("enable_sample_flag", event.params["flag_key"])
        assertEquals("true", event.params["enabled"])
    }

    @Test
    @DisplayName("Given events recorded when cleared then drops all events")
    fun clearDropsEvents() {
        fake.logEvent("flag_exposure")
        fake.clear()

        assertTrue(fake.events.isEmpty())
    }
}

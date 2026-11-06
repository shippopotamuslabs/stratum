package com.stratum.feature.settings

import com.stratum.core.testing.telemetry.FakeAnalyticsPort
import com.stratum.domain.telemetry.PARAM_ACTION
import com.stratum.domain.telemetry.PARAM_SCREEN
import com.stratum.domain.telemetry.USER_VISIBLE_ERROR_EVENT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AnalyticsRecorderTest {
    private val fakeAnalytics = FakeAnalyticsPort()
    private val recorder = AnalyticsRecorder(fakeAnalytics)

    @Test
    fun `invoke records event with params`() {
        recorder(
            USER_VISIBLE_ERROR_EVENT,
            mapOf(PARAM_SCREEN to "create_program", PARAM_ACTION to "generate_program"),
        )

        val event = fakeAnalytics.events.single()
        assertEquals(USER_VISIBLE_ERROR_EVENT, event.name)
        assertEquals("create_program", event.params[PARAM_SCREEN])
        assertEquals("generate_program", event.params[PARAM_ACTION])
    }

    @Test
    fun `invoke records event without params when omitted`() {
        recorder("session_start")

        val event = fakeAnalytics.events.single()
        assertEquals("session_start", event.name)
        assertEquals(emptyMap<String, String>(), event.params)
    }
}

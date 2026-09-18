package com.stratum.domain.logging

import com.stratum.core.testing.telemetry.FakeAnalyticsPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LogAnalyticsEventUseCaseTest {
    private val fakeAnalytics = FakeAnalyticsPort()
    private val useCase = LogAnalyticsEventUseCase(fakeAnalytics)

    @Test
    fun `invoke records event with params`() {
        useCase(
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
        useCase("session_start")

        val event = fakeAnalytics.events.single()
        assertEquals("session_start", event.name)
        assertEquals(emptyMap<String, String>(), event.params)
    }
}

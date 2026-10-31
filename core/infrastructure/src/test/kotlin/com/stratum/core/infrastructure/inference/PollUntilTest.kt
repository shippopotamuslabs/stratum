package com.stratum.core.infrastructure.inference

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Timeout-budget behavior of the shared [pollUntil] loop, including the STR-55
 * suspension-gap policy. Uses plain string snapshots so this stays decoupled
 * from any one cloud job type.
 */
class PollUntilTest {
    /** Deterministic [MonotonicClock]; sleep is modeled by advancing it mid-poll. */
    private class FakeClock(var now: Long = 1_000L) {
        val clock = MonotonicClock { now }

        fun advance(ms: Long) {
            now += ms
        }
    }

    @Test
    @DisplayName("poll loop excludes a sleep gap from the timeout budget")
    fun pollLoopExcludesSleepGap() =
        runTest {
            val fake = FakeClock()
            var polls = 0
            val result =
                pollUntil(
                    timeoutMs = 5_000L,
                    initialIntervalMs = 10L,
                    slowIntervalMs = 10L,
                    backoffAfterMs = 60_000L,
                    isTerminal = { it == "ready" },
                    poll = {
                        polls++
                        // Models the device sleeping for two minutes mid-poll:
                        // without STR-55 the 5s budget is blown on wake.
                        if (polls == 2) fake.advance(120_000L)
                        if (polls < 3) "pending" else "ready"
                    },
                    clock = fake.clock,
                )
            assertEquals("ready", result)
            assertEquals(3, polls)
        }

    @Test
    @DisplayName("poll loop still enforces the active polling budget")
    fun pollLoopEnforcesActiveBudget() =
        runTest {
            val fake = FakeClock()
            var thrown: Throwable? = null
            try {
                pollUntil(
                    timeoutMs = 100L,
                    initialIntervalMs = 10L,
                    slowIntervalMs = 10L,
                    backoffAfterMs = 60_000L,
                    isTerminal = { it == "ready" },
                    poll = {
                        fake.advance(10L)
                        "pending"
                    },
                    clock = fake.clock,
                )
            } catch (e: IllegalStateException) {
                thrown = e
            }
            assertTrue(thrown is IllegalStateException)
        }
}

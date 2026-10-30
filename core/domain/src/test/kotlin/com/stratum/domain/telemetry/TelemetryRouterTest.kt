package com.stratum.domain.telemetry

import com.stratum.core.testing.telemetry.FakeAnalyticsPort
import com.stratum.core.testing.telemetry.FakeCrashTelemetryPort
import com.stratum.core.testing.telemetry.FakeOutcomeTelemetrySink
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TelemetryRouterTest {
    private val sink = FakeOutcomeTelemetrySink()
    private val crash = FakeCrashTelemetryPort()
    private val analytics = FakeAnalyticsPort()
    private val router = TelemetryRouter(sink, crash, analytics)

    @Test
    fun `success outcome is recorded exactly once with the same fact`() {
        val outcome =
            OutcomeTelemetry(
                useCase = OutcomeTelemetry.USE_CASE_PROGRAM_GENERATION,
                result = OutcomeResult.Success,
                durationMs = 123L,
            )

        router.emitOutcome(outcome)

        assertEquals(listOf(outcome), sink.outcomes)
    }

    @Test
    fun `expected failure outcome is recorded on the same single store`() {
        val outcome =
            OutcomeTelemetry(
                useCase = OutcomeTelemetry.USE_CASE_CLOUD_SYNC,
                result = OutcomeResult.ExpectedFailure(OutcomeTelemetry.REASON_SYNC_NOT_STARTED),
            )

        router.emitOutcome(outcome)

        assertEquals(listOf(outcome), sink.outcomes)
    }

    @Test
    fun `emissions accumulate without fan-out`() {
        val first =
            OutcomeTelemetry(
                useCase = OutcomeTelemetry.USE_CASE_CLOUD_SYNC,
                result = OutcomeResult.Success,
            )
        val second =
            OutcomeTelemetry(
                useCase = OutcomeTelemetry.USE_CASE_CLOUD_SYNC,
                result = OutcomeResult.ExpectedFailure(OutcomeTelemetry.REASON_SYNC_RESIDUAL_BACKLOG),
            )

        router.emitOutcome(first)
        router.emitOutcome(second)

        assertEquals(listOf(first, second), sink.outcomes)
    }

    @Test
    fun `outcome wire names are stable`() {
        assertEquals("success", OutcomeResult.Success.wireName)
        assertEquals(
            "expected_failure",
            OutcomeResult.ExpectedFailure(OutcomeTelemetry.REASON_SYNTHESIS_FAILED).wireName,
        )
    }

    @Test
    fun `blank use case is rejected`() {
        assertThrows<IllegalArgumentException> {
            OutcomeTelemetry(useCase = "  ", result = OutcomeResult.Success)
        }
    }

    @Test
    fun `blank reason code is rejected`() {
        assertThrows<IllegalArgumentException> {
            OutcomeTelemetry(
                useCase = OutcomeTelemetry.USE_CASE_CLOUD_SYNC,
                result = OutcomeResult.ExpectedFailure("  "),
            )
        }
    }

    @Test
    fun `negative duration is rejected`() {
        assertThrows<IllegalArgumentException> {
            OutcomeTelemetry(
                useCase = OutcomeTelemetry.USE_CASE_CLOUD_SYNC,
                result = OutcomeResult.Success,
                durationMs = -1L,
            )
        }
    }

    @Test
    fun `free-text reason detail is rejected to bound series cardinality`() {
        assertThrows<IllegalArgumentException> {
            OutcomeResult.ExpectedFailure("quota exceeded for user alice@example.com")
        }
        assertTrue(sink.outcomes.isEmpty())
    }

    @Test
    fun `exceptional is recorded once as non-fatal plus one bare counter`() {
        val failure = IllegalStateException("permission denied")

        router.recordExceptional("create_program_generate", failure)

        assertSame(failure, crash.recordedExceptions.single())
        val counter = analytics.events.single()
        assertEquals(TelemetryRouter.EVENT_EXCEPTIONAL_OCCURRED, counter.name)
        assertEquals("create_program_generate", counter.params[TelemetryRouter.PARAM_BOUNDARY])
        assertEquals("IllegalStateException", counter.params[TelemetryRouter.PARAM_CLASS])
        assertTrue(sink.outcomes.isEmpty(), "exceptional must not enter the outcome stream")
    }

    @Test
    fun `emitOutcome touches neither crash nor analytics`() {
        router.emitOutcome(
            OutcomeTelemetry(
                useCase = OutcomeTelemetry.USE_CASE_CLOUD_SYNC,
                result = OutcomeResult.Success,
            ),
        )

        assertTrue(crash.recordedExceptions.isEmpty())
        assertTrue(crash.breadcrumbs.isEmpty())
        assertTrue(analytics.events.isEmpty())
        assertEquals(1, sink.outcomes.size)
    }

    @Test
    fun `recordExceptional carries no payload or stack in the counter`() {
        val failure = RuntimeException("secret token abc123\n\tat hidden.StackTrace")

        router.recordExceptional("cloud_sync_worker", failure)

        val counter = analytics.events.single()
        assertEquals(2, counter.params.size)
        assertEquals("cloud_sync_worker", counter.params[TelemetryRouter.PARAM_BOUNDARY])
        assertEquals("RuntimeException", counter.params[TelemetryRouter.PARAM_CLASS])
    }
}

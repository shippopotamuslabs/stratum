package com.stratum.domain.telemetry

import com.stratum.core.testing.telemetry.FakeOutcomeTelemetrySink
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TelemetryRouterTest {
    private val sink = FakeOutcomeTelemetrySink()
    private val router = TelemetryRouter(sink)

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
}

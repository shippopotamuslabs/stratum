package com.stratum.core.infrastructure.work

import androidx.work.ListenableWorker
import com.stratum.core.infrastructure.sync.CloudSyncResidualException
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.domain.telemetry.OutcomeResult
import com.stratum.domain.telemetry.OutcomeTelemetry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CloudSyncWorkResultTest {
    private val fakeLogging = FakeLoggingPort()

    @Test
    fun `success outcome maps to success`() {
        val result =
            mapCloudSyncWorkResult(
                failure = null,
                runAttemptCount = 0,
                loggingPort = fakeLogging,
            )

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(fakeLogging.errors.isEmpty())
        assertTrue(fakeLogging.warnings.isEmpty())
    }

    @Test
    fun `not started maps to success without retry`() {
        val result =
            mapCloudSyncWorkResult(
                failure = CloudSyncNotStartedException(),
                runAttemptCount = 0,
                loggingPort = fakeLogging,
            )

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `residual exception retries without consuming bounded attempts`() {
        val result =
            mapCloudSyncWorkResult(
                failure = CloudSyncResidualException("dirty leftover"),
                runAttemptCount = 99,
                loggingPort = fakeLogging,
            )

        assertEquals(ListenableWorker.Result.retry(), result)
        assertTrue(fakeLogging.errors.isEmpty())
        assertTrue(fakeLogging.warnings.isEmpty())
    }

    @Test
    fun `other failures use bounded retry then give up`() {
        val error = IllegalStateException("permission denied")
        val retry =
            mapCloudSyncWorkResult(
                failure = error,
                runAttemptCount = 1,
                loggingPort = fakeLogging,
            )
        val giveUp =
            mapCloudSyncWorkResult(
                failure = error,
                runAttemptCount = DEFAULT_WORKER_MAX_RETRIES,
                loggingPort = fakeLogging,
            )

        assertEquals(ListenableWorker.Result.retry(), retry)
        assertEquals(ListenableWorker.Result.failure(), giveUp)
        assertEquals(1, fakeLogging.warnings.size)
        assertEquals(1, fakeLogging.errors.size)
        assertEquals(error, fakeLogging.errors.single().throwable)
    }

    @Test
    fun `successful sync classifies to success outcome`() {
        val outcome = classifySyncOutcome(null)

        assertEquals(
            OutcomeTelemetry(
                useCase = OutcomeTelemetry.USE_CASE_CLOUD_SYNC,
                result = OutcomeResult.Success,
            ),
            outcome,
        )
    }

    @Test
    fun `not started classifies to expected failure`() {
        val outcome = classifySyncOutcome(CloudSyncNotStartedException())

        assertEquals(
            OutcomeTelemetry(
                useCase = OutcomeTelemetry.USE_CASE_CLOUD_SYNC,
                result = OutcomeResult.ExpectedFailure(OutcomeTelemetry.REASON_SYNC_NOT_STARTED),
            ),
            outcome,
        )
    }

    @Test
    fun `residual backlog classifies to expected failure`() {
        val outcome = classifySyncOutcome(CloudSyncResidualException("dirty"))

        assertEquals(
            OutcomeTelemetry(
                useCase = OutcomeTelemetry.USE_CASE_CLOUD_SYNC,
                result = OutcomeResult.ExpectedFailure(OutcomeTelemetry.REASON_SYNC_RESIDUAL_BACKLOG),
            ),
            outcome,
        )
    }

    @Test
    fun `unexpected failure has no outcome home`() {
        assertNull(classifySyncOutcome(IllegalStateException("permission denied")))
    }

    @Test
    fun `give up with terminal hook records instead of logging`() {
        val error = IllegalStateException("permission denied")
        val recorded = mutableListOf<Throwable>()

        val result =
            mapCloudSyncWorkResult(
                failure = error,
                runAttemptCount = DEFAULT_WORKER_MAX_RETRIES,
                loggingPort = fakeLogging,
                onTerminalExceptional = recorded::add,
            )

        assertEquals(ListenableWorker.Result.failure(), result)
        assertEquals(listOf(error), recorded)
        assertTrue(fakeLogging.errors.isEmpty(), "terminal record replaces the give-up error log")
    }

    @Test
    fun `retry attempt with terminal hook still retries without recording`() {
        val error = IllegalStateException("permission denied")
        val recorded = mutableListOf<Throwable>()

        val result =
            mapCloudSyncWorkResult(
                failure = error,
                runAttemptCount = 1,
                loggingPort = fakeLogging,
                onTerminalExceptional = recorded::add,
            )

        assertEquals(ListenableWorker.Result.retry(), result)
        assertTrue(recorded.isEmpty(), "intermediate retries are not terminal")
    }
}

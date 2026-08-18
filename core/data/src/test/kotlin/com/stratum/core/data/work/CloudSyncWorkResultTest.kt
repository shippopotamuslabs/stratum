package com.stratum.core.data.work

import androidx.work.ListenableWorker
import com.stratum.core.data.sync.CloudSyncResidualException
import com.stratum.core.testing.logging.FakeLoggingPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CloudSyncWorkResultTest {
    private val fakeLogging = FakeLoggingPort()

    @Test
    fun `success outcome maps to success`() {
        val result =
            mapCloudSyncWorkResult(
                outcome = Result.success(Unit),
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
                outcome = Result.failure(CloudSyncNotStartedException()),
                runAttemptCount = 0,
                loggingPort = fakeLogging,
            )

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `residual exception retries without consuming bounded attempts`() {
        val result =
            mapCloudSyncWorkResult(
                outcome = Result.failure(CloudSyncResidualException("dirty leftover")),
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
                outcome = Result.failure(error),
                runAttemptCount = 1,
                loggingPort = fakeLogging,
            )
        val giveUp =
            mapCloudSyncWorkResult(
                outcome = Result.failure(error),
                runAttemptCount = DEFAULT_WORKER_MAX_RETRIES,
                loggingPort = fakeLogging,
            )

        assertEquals(ListenableWorker.Result.retry(), retry)
        assertEquals(ListenableWorker.Result.failure(), giveUp)
        assertEquals(1, fakeLogging.warnings.size)
        assertEquals(1, fakeLogging.errors.size)
        assertEquals(error, fakeLogging.errors.single().throwable)
    }
}

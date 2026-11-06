package com.stratum.application.sync

import com.stratum.core.testing.sync.FakeCloudSyncPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.stratum.domain.sync.CloudSessionState

class CloudSyncUseCasesTest {
    @Test
    fun delegatesToPort() = runTest {
        val port = FakeCloudSyncPort()
        val start = StartCloudSyncUseCase(port)
        val stop = StopCloudSyncUseCase(port)
        val request = RequestCloudSyncUseCase(port)
        val observeSession = ObserveCloudSessionStateUseCase(port)
        val wipe = EndSessionAndWipeUseCase(port)

        assertEquals(CloudSessionState.Inactive, observeSession().value)

        assertEquals(CloudSyncOutcome.Success, start("uid-1"))
        assertEquals("uid-1", port.startedUserId)
        assertEquals(1, port.startCount)
        assertEquals(CloudSessionState.Ready("uid-1"), observeSession().value)

        assertEquals(CloudSyncOutcome.Success, request())
        assertEquals(1, port.requestCount)

        stop()
        assertEquals(1, port.stopCount)
        assertEquals(null, port.startedUserId)
        assertEquals(CloudSessionState.Inactive, observeSession().value)

        assertEquals(CloudSyncOutcome.Success, wipe("uid-1"))
        assertEquals(1, port.wipeCount)
        assertEquals("uid-1", port.lastWipedUserId)
        assertTrue(port.callOrder.contains("wipe"))
    }

    @Test
    fun portFailureReturnsFailedOutcome() = runTest {
        val port = FakeCloudSyncPort()
        port.startFailure = IllegalStateException("sync boom")
        val start = StartCloudSyncUseCase(port)

        // Failed is a payload-free branch: the throwable's single record is
        // the port adapter's own boundary log, so nothing rides the outcome.
        assertEquals(CloudSyncOutcome.Failed, start("uid-1"))
    }

    @Test
    fun restartDiagnosticsCarryTheRestartFailureCause() = runTest {
        val port = FakeCloudSyncPort()
        val failure = IllegalStateException("sync boom")
        port.startFailure = failure
        val start = StartCloudSyncUseCase(port)

        val diagnostics = start.restartDiagnostics("uid-1")

        assertEquals(1, diagnostics.size)
        assertSame(
            failure,
            (diagnostics.single() as CloudSyncDiagnostic.OperationFailed).cause,
        )
    }

    @Test
    fun restartDiagnosticsEmptyOnSuccess() = runTest {
        val port = FakeCloudSyncPort()
        val start = StartCloudSyncUseCase(port)

        assertTrue(start.restartDiagnostics("uid-1").isEmpty())
        assertEquals(1, port.startCount)
    }

    @Test
    fun portCancellationPropagates() = runTest {
        val port = FakeCloudSyncPort()
        port.requestFailure = CancellationException("cancelled")
        val request = RequestCloudSyncUseCase(port)

        var thrown: Throwable? = null
        try {
            request()
        } catch (error: CancellationException) {
            thrown = error
        }

        assertTrue(thrown is CancellationException)
    }
}

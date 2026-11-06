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
    fun portFailureReturnsFailedOutcomeWithDiagnostic() = runTest {
        val port = FakeCloudSyncPort()
        val failure = IllegalStateException("sync boom")
        port.startFailure = failure
        val start = StartCloudSyncUseCase(port)

        val outcome = start("uid-1")

        assertTrue(outcome is CloudSyncOutcome.Failed)
        val diagnostics = (outcome as CloudSyncOutcome.Failed).diagnostics
        assertEquals(1, diagnostics.size)
        assertSame(
            failure,
            (diagnostics.single() as CloudSyncDiagnostic.OperationFailed).cause,
        )
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

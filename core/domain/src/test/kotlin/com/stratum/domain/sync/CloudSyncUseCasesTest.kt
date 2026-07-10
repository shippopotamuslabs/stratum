package com.stratum.domain.sync

import com.stratum.core.testing.sync.FakeCloudSyncPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CloudSyncUseCasesTest {
    @Test
    fun `start stop and request delegate to port`() = runTest {
        val port = FakeCloudSyncPort()
        val start = StartCloudSyncUseCase(port)
        val stop = StopCloudSyncUseCase(port)
        val request = RequestCloudSyncUseCase(port)

        assertEquals(Result.success(Unit), start("uid-1"))
        assertEquals("uid-1", port.startedUserId)
        assertEquals(1, port.startCount)

        assertEquals(Result.success(Unit), request())
        assertEquals(1, port.requestCount)

        stop()
        assertEquals(1, port.stopCount)
        assertEquals(null, port.startedUserId)
    }
}

@file:Suppress("ForbiddenMockKInTests") // Firestore sync state store and SDK boundaries — no fakes available.

package com.stratum.core.infrastructure.sync

import com.stratum.core.testing.logging.FakeLoggingPort
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class FirestorePendingUploadEngineTest {

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @DisplayName("Given the upload pass throws when uploadPending then Result failure carries it and engine records nothing")
    fun uploadPendingFailureRecordsNothing() = runTest {
        val uploadFailure = IllegalStateException("upload blew up")
        val logging = FakeLoggingPort()
        val stateStore = mockk<FirestoreSyncStateStore> {
            coEvery { deviceId() } throws uploadFailure
        }
        val subject = FirestorePendingUploadEngine(
            firestore = mockk(relaxed = true),
            stateStore = stateStore,
            handlers = mockk(relaxed = true),
            pendingCloudDeleteDao = mockk(relaxed = true),
            writeCoordinator = UserOwnedWriteCoordinator(),
            loggingPort = logging,
        )

        val result = subject.uploadPending("uid-1")

        assertTrue(result.isFailure)
        assertSame(uploadFailure, result.exceptionOrNull())
        // STR-238: the failure rides the Result to the adapter's drained
        // outcome; the engine itself records nothing on the propagate path.
        assertTrue(logging.errors.isEmpty())
    }

    @Test
    @DisplayName("Given the upload pass throws when uploadUntilIdle then Result failure carries it and engine records nothing")
    fun uploadUntilIdleFailureRecordsNothing() = runTest {
        val uploadFailure = IllegalStateException("upload blew up")
        val logging = FakeLoggingPort()
        val stateStore = mockk<FirestoreSyncStateStore> {
            coEvery { deviceId() } throws uploadFailure
        }
        val subject = FirestorePendingUploadEngine(
            firestore = mockk(relaxed = true),
            stateStore = stateStore,
            handlers = mockk(relaxed = true),
            pendingCloudDeleteDao = mockk(relaxed = true),
            writeCoordinator = UserOwnedWriteCoordinator(),
            loggingPort = logging,
        )

        val result = subject.uploadUntilIdle("uid-1")

        assertTrue(result.isFailure)
        assertSame(uploadFailure, result.exceptionOrNull())
        assertTrue(logging.errors.isEmpty())
    }
}

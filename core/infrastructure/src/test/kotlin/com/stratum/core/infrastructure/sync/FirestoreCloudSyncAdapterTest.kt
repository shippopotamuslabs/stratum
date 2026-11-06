@file:Suppress("ForbiddenMockKInTests") // Concrete Firestore sync engines and the WorkManager scheduler — no fakes available.

package com.stratum.core.infrastructure.sync

import com.stratum.application.training.RebuildLocalDerivedTrainingDataUseCase
import com.stratum.core.infrastructure.work.CloudSyncScheduler
import com.stratum.core.testing.logging.FakeLoggingPort
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class FirestoreCloudSyncAdapterTest {

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    /** A [Throwable] that is not an [Exception] — the recovery enqueue catch must be Throwable-wide. */
    private class EnqueueThrowable(message: String) : Throwable(message)

    @Test
    @DisplayName("Given push/pull fails and recovery enqueueSoon throws when start then recovery failure is recorded exactly once")
    fun startRecoveryEnqueueFailureIsRecordedExactlyOnce() = runTest {
        val pushPullFailure = IllegalStateException("push failed")
        val enqueueFailure = EnqueueThrowable("enqueue blew up")
        val logging = FakeLoggingPort()

        val stateStore = mockk<FirestoreSyncStateStore> {
            coEvery { lastSyncedUserId() } returns "uid-1"
            coEvery { setLastSyncedUserId(any()) } returns Unit
            coEvery { isHydrated(any()) } returns false
        }
        val uploadEngine = mockk<FirestorePendingUploadEngine> {
            coEvery { uploadUntilIdle(any(), any()) } returns Result.failure(pushPullFailure)
        }
        val scheduler = mockk<CloudSyncScheduler> {
            every { enqueueSoon() } throws enqueueFailure
        }
        val subject = FirestoreCloudSyncAdapter(
            stateStore = stateStore,
            uploadEngine = uploadEngine,
            pullEngine = mockk(relaxed = true),
            userOwnedDataClearer = mockk(relaxed = true),
            writeCoordinator = UserOwnedWriteCoordinator(),
            catalogSeedGate = LiftCatalogSeedGate().apply { complete() },
            cloudSyncScheduler = scheduler,
            featureGate = DefaultCloudSyncFeatureGate().apply { setEnabledForTests(true) },
            rebuildLocalDerivedTrainingData = mockk<RebuildLocalDerivedTrainingDataUseCase>(relaxed = true),
            loggingPort = logging,
            ioDispatcher = Dispatchers.Unconfined,
        )

        // The push/pull outcome logic still ran: first-hydrate failure is thrown.
        var thrown: Throwable? = null
        try {
            subject.start("uid-1")
        } catch (error: Throwable) {
            thrown = error
        }

        assertSame(pushPullFailure, thrown)
        val recoveryErrors = logging.errors.filter {
            it.message.startsWith("start recovery enqueue failed")
        }
        assertEquals(1, recoveryErrors.size)
        assertSame(enqueueFailure, recoveryErrors.single().throwable)
    }
}

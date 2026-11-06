@file:Suppress("ForbiddenMockKInTests") // Concrete Firestore sync engines and the WorkManager scheduler — no fakes available.

package com.stratum.core.infrastructure.sync

import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.application.training.RebuildLocalDerivedTrainingDataResult
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class FirestoreCloudSyncAdapterTest {

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    /** A [Throwable] that is not an [Exception] — the recovery enqueue catch must be Throwable-wide. */
    private class EnqueueThrowable(message: String) : Throwable(message)

    /** An [Error] — not an [Exception] — thrown from the push/pull seam (A1). */
    private class PushPullError(message: String) : Error(message)

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

        // The push/pull outcome logic still ran: a first-hydrate failure is
        // the Failed branch, its cause riding as the diagnostic value.
        val outcome = subject.start("uid-1")

        assertTrue(outcome is com.stratum.application.sync.CloudSyncOutcome.Failed)
        assertSame(
            pushPullFailure,
            ((outcome as com.stratum.application.sync.CloudSyncOutcome.Failed)
                .diagnostics.single()
                as com.stratum.application.sync.CloudSyncDiagnostic.OperationFailed).cause,
        )
        val recoveryErrors = logging.errors.filter {
            it.message.startsWith("start recovery enqueue failed")
        }
        assertEquals(1, recoveryErrors.size)
        assertSame(enqueueFailure, recoveryErrors.single().throwable)
        // STR-238: the B12 recovery-enqueue record is the ONLY adapter record —
        // the primary push/pull failure itself records nothing here (its
        // diagnostics ride the Failed outcome to the boundary drain).
        assertEquals(1, logging.errors.size)
    }

    @Test
    @DisplayName("Given push/pull throws an Error when start then Failed outcome carries it instead of throwing")
    fun startPushPullErrorBecomesFailedOutcome() = runTest {
        val pushPullFailure = PushPullError("native blew up")
        val logging = FakeLoggingPort()

        val stateStore = mockk<FirestoreSyncStateStore> {
            coEvery { lastSyncedUserId() } returns "uid-1"
            coEvery { setLastSyncedUserId(any()) } returns Unit
            coEvery { isHydrated(any()) } returns false
        }
        val uploadEngine = mockk<FirestorePendingUploadEngine> {
            coEvery { uploadUntilIdle(any(), any()) } returns Result.failure(pushPullFailure)
        }
        val subject = FirestoreCloudSyncAdapter(
            stateStore = stateStore,
            uploadEngine = uploadEngine,
            pullEngine = mockk(relaxed = true),
            userOwnedDataClearer = mockk(relaxed = true),
            writeCoordinator = UserOwnedWriteCoordinator(),
            catalogSeedGate = LiftCatalogSeedGate().apply { complete() },
            cloudSyncScheduler = mockk(relaxed = true),
            featureGate = DefaultCloudSyncFeatureGate().apply { setEnabledForTests(true) },
            rebuildLocalDerivedTrainingData = mockk<RebuildLocalDerivedTrainingDataUseCase>(relaxed = true),
            loggingPort = logging,
            ioDispatcher = Dispatchers.Unconfined,
        )

        // An Error escaping the port as a throw would lose the sync worker's
        // classification; the widened catch returns it as the Failed branch.
        val outcome = subject.start("uid-1")

        assertTrue(outcome is com.stratum.application.sync.CloudSyncOutcome.Failed)
        assertSame(
            pushPullFailure,
            ((outcome as com.stratum.application.sync.CloudSyncOutcome.Failed)
                .diagnostics.single()
                as com.stratum.application.sync.CloudSyncDiagnostic.OperationFailed).cause,
        )
        // STR-238: the primary failure records nothing at the adapter.
        assertTrue(logging.errors.isEmpty())
    }

    @Test
    @DisplayName("Given account ownership setup throws when start then Failed carries the cause and adapter records nothing")
    fun startOwnershipFailureRecordsNothingAtAdapter() = runTest {
        val ownershipFailure = IllegalStateException("ownership blew up")
        val logging = FakeLoggingPort()
        val stateStore = mockk<FirestoreSyncStateStore> {
            coEvery { lastSyncedUserId() } throws ownershipFailure
        }
        val subject = adapter(stateStore = stateStore, logging = logging)

        val outcome = subject.start("uid-1")

        assertTrue(outcome is com.stratum.application.sync.CloudSyncOutcome.Failed)
        assertSame(
            ownershipFailure,
            ((outcome as com.stratum.application.sync.CloudSyncOutcome.Failed)
                .diagnostics.single()
                as com.stratum.application.sync.CloudSyncDiagnostic.OperationFailed).cause,
        )
        assertTrue(logging.errors.isEmpty())
    }

    @Test
    @DisplayName("Given a started session when requestSync push pull fails then Failed carries the cause and adapter records nothing")
    fun requestSyncFailureRecordsNothingAtAdapter() = runTest {
        val syncFailure = IllegalStateException("requestSync blew up")
        val logging = FakeLoggingPort()
        val stateStore = mockk<FirestoreSyncStateStore> {
            coEvery { lastSyncedUserId() } returns "uid-1"
            coEvery { setLastSyncedUserId(any()) } returns Unit
            coEvery { isHydrated(any()) } returns true
        }
        val uploadEngine = mockk<FirestorePendingUploadEngine> {
            coEvery { uploadUntilIdle(any(), any()) } returns Result.success(false)
            coEvery { hasResidualPending() } returns false
        }
        val pullEngine = mockk<FirestoreRemotePullEngine> {
            coEvery { pull(any()) } returns
                Result.success(PullResult(hadFailures = false, hydrated = true))
        }
        val subject = adapter(
            stateStore = stateStore,
            uploadEngine = uploadEngine,
            pullEngine = pullEngine,
            logging = logging,
        )

        val startOutcome = subject.start("uid-1")
        assertTrue(startOutcome is com.stratum.application.sync.CloudSyncOutcome.Success)

        coEvery { uploadEngine.uploadUntilIdle(any(), any()) } returns Result.failure(syncFailure)
        val outcome = subject.requestSync()

        assertTrue(outcome is com.stratum.application.sync.CloudSyncOutcome.Failed)
        assertSame(
            syncFailure,
            ((outcome as com.stratum.application.sync.CloudSyncOutcome.Failed)
                .diagnostics.single()
                as com.stratum.application.sync.CloudSyncDiagnostic.OperationFailed).cause,
        )
        assertTrue(logging.errors.isEmpty())
    }

    @Test
    @DisplayName("Given the local wipe throws when endSessionAndWipe then Failed carries the cause and adapter records nothing")
    fun endSessionAndWipeFailureRecordsNothingAtAdapter() = runTest {
        val wipeFailure = IllegalStateException("wipe blew up")
        val logging = FakeLoggingPort()
        val clearer = mockk<UserOwnedDataClearer> {
            coEvery { clearUserOwnedData() } throws wipeFailure
        }
        val subject = adapter(
            stateStore = mockk(relaxed = true),
            userOwnedDataClearer = clearer,
            logging = logging,
        )

        val outcome = subject.endSessionAndWipe("uid-1")

        assertTrue(outcome is com.stratum.application.sync.CloudSyncOutcome.Failed)
        assertSame(
            wipeFailure,
            ((outcome as com.stratum.application.sync.CloudSyncOutcome.Failed)
                .diagnostics.single()
                as com.stratum.application.sync.CloudSyncDiagnostic.OperationFailed).cause,
        )
        assertTrue(logging.errors.isEmpty())
    }

    @Test
    @DisplayName("Given the post-pull rebuild fails when start then sync succeeds and the rebuild diagnostic is recorded exactly once with no breadcrumb")
    fun postPullRebuildFailureIsDrainedExactlyOnceWithoutBreadcrumb() = runTest {
        val rebuildFailure = IllegalStateException("weekly rebuild blew up")
        val rebuildDiagnostic = OperationalDiagnostic(
            tag = "RebuildLocalDerived",
            message = "Weekly series rebuild failed: ${rebuildFailure.message}",
            cause = rebuildFailure,
        )
        val logging = FakeLoggingPort()
        val stateStore = mockk<FirestoreSyncStateStore> {
            coEvery { lastSyncedUserId() } returns "uid-1"
            coEvery { setLastSyncedUserId(any()) } returns Unit
            coEvery { isHydrated(any()) } returns true
        }
        val uploadEngine = mockk<FirestorePendingUploadEngine> {
            coEvery { uploadUntilIdle(any(), any()) } returns Result.success(false)
            coEvery { hasResidualPending() } returns false
        }
        val pullEngine = mockk<FirestoreRemotePullEngine> {
            coEvery { pull(any()) } returns
                Result.success(PullResult(hadFailures = false, hydrated = true))
        }
        val subject = adapter(
            stateStore = stateStore,
            uploadEngine = uploadEngine,
            pullEngine = pullEngine,
            logging = logging,
            rebuildResult = RebuildLocalDerivedTrainingDataResult.PersistenceFailed(
                listOf(rebuildDiagnostic),
            ),
        )

        // Sync itself succeeded; the rebuild failure does not fail the pass.
        val outcome = subject.start("uid-1")

        assertTrue(outcome is com.stratum.application.sync.CloudSyncOutcome.Success)
        // STR-238 (B13): the drained per-diagnostic record is the rebuild
        // failure's only record — exactly one error, carrying the real
        // cause, and no duplicate "post-pull derived rebuild failed"
        // breadcrumb on top of it.
        assertEquals(1, logging.errors.size)
        val record = logging.errors.single()
        assertEquals(rebuildDiagnostic.tag, record.tag)
        assertEquals(rebuildDiagnostic.message, record.message)
        assertSame(rebuildFailure, record.throwable)
        assertTrue(logging.errors.none { it.message == "post-pull derived rebuild failed" })
    }

    private fun adapter(
        stateStore: FirestoreSyncStateStore,
        uploadEngine: FirestorePendingUploadEngine = mockk(relaxed = true),
        pullEngine: FirestoreRemotePullEngine = mockk(relaxed = true),
        userOwnedDataClearer: UserOwnedDataClearer = mockk(relaxed = true),
        logging: FakeLoggingPort,
        rebuildResult: RebuildLocalDerivedTrainingDataResult =
            RebuildLocalDerivedTrainingDataResult.Success,
    ): FirestoreCloudSyncAdapter {
        // Explicit result: a relaxed mock of the sealed result can take the
        // PersistenceFailed branch and trip the adapter's (legal) rebuild drain.
        val rebuild = mockk<RebuildLocalDerivedTrainingDataUseCase>()
        coEvery { rebuild.invoke() } returns rebuildResult
        return FirestoreCloudSyncAdapter(
            stateStore = stateStore,
            uploadEngine = uploadEngine,
            pullEngine = pullEngine,
            userOwnedDataClearer = userOwnedDataClearer,
            writeCoordinator = UserOwnedWriteCoordinator(),
            catalogSeedGate = LiftCatalogSeedGate().apply { complete() },
            cloudSyncScheduler = mockk(relaxed = true),
            featureGate = DefaultCloudSyncFeatureGate().apply { setEnabledForTests(true) },
            rebuildLocalDerivedTrainingData = rebuild,
            loggingPort = logging,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }
}

package com.stratum.core.infrastructure.sync
import com.stratum.core.infrastructure.sync.CloudSyncResidualException
import com.stratum.core.infrastructure.sync.UserOwnedWriteCoordinator
import com.stratum.core.infrastructure.sync.CloudSyncFeatureGate

import com.stratum.core.infrastructure.work.CloudSyncScheduler
import com.stratum.domain.di.IoDispatcher
import com.stratum.core.infrastructure.logging.LoggingPort
import com.stratum.domain.sync.CloudSessionState
import com.stratum.application.sync.CLOUD_SYNC_LOG_TAG
import com.stratum.application.sync.CloudSyncDiagnostic
import com.stratum.application.sync.CloudSyncNotStartedException
import com.stratum.application.sync.CloudSyncOutcome
import com.stratum.application.sync.CloudSyncPort
import com.stratum.domain.sync.SyncStatus
import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.application.training.RebuildLocalDerivedTrainingDataResult
import com.stratum.application.training.RebuildLocalDerivedTrainingDataUseCase
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Production [CloudSyncPort]: upload pending first, then pull, then post-pull residual upload.
 *
 * Account ownership is durable ([FirestoreSyncStateStore.lastSyncedUserId]): switching users
 * always wipes Room before any upload, even after a sign-out that cleared in-memory session
 * state — **including when the feature gate is off** (kill-switch must not disable isolation).
 *
 * [stop] only tears down session (active uid + WorkManager); durable hydrate/cursor state is kept
 * so same-user re-login can incremental-pull **unless** [endSessionAndWipe] ran (logout wipe of
 * Room + cursors). Logout keeps [FirestoreSyncStateStore.lastSyncedUserId] so the next [start]
 * still detects a different uid and wipes leftover rows.
 *
 * [sessionState] becomes [CloudSessionState.Ready] once local Room is owned/usable for the
 * active user (already hydrated, ownership-only gate off, or first successful push/pull).
 * Network failures after local-ready leave Ready set and recover via WorkManager.
 *
 * After a clean push/pull, [RebuildLocalDerivedTrainingDataUseCase] recomputes non-synced
 * weekly dashboard tables from hydrated training logs (Gym history works without this; charts
 * do not). Rebuild failures are logged and do not fail the sync pass.
 *
 * Adapter mutex serializes [start]/[stop]/[requestSync]/[endSessionAndWipe] against each
 * other only — Room write isolation is [UserOwnedWriteCoordinator] (repos + remote apply/adopt).
 *
 * Residual work throws [CloudSyncResidualException] internally so [requestSync] returns a Failed outcome and
 * WorkManager can [androidx.work.ListenableWorker.Result.retry] — never REPLACE unique work from
 * inside a running worker pass.
 */
@Singleton
class FirestoreCloudSyncAdapter @Inject constructor(
    private val stateStore: FirestoreSyncStateStore,
    private val uploadEngine: FirestorePendingUploadEngine,
    private val pullEngine: FirestoreRemotePullEngine,
    private val userOwnedDataClearer: UserOwnedDataClearer,
    private val writeCoordinator: UserOwnedWriteCoordinator,
    private val catalogSeedGate: LiftCatalogSeedGate,
    private val cloudSyncScheduler: CloudSyncScheduler,
    private val featureGate: CloudSyncFeatureGate,
    private val rebuildLocalDerivedTrainingData: RebuildLocalDerivedTrainingDataUseCase,
    private val loggingPort: LoggingPort,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CloudSyncPort {
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _sessionState = MutableStateFlow<CloudSessionState>(CloudSessionState.Inactive)
    override val sessionState: StateFlow<CloudSessionState> = _sessionState.asStateFlow()

    private val activeUserId = AtomicReference<String?>(null)
    private val mutex = Mutex()

    override suspend fun start(userId: String): CloudSyncOutcome =
        withContext(ioDispatcher) {
            mutex.withLock {
                _sessionState.value = CloudSessionState.Establishing(userId)
                // Local-first flag, set by the setup block below before push/pull runs.
                var localAlreadyUsable = false
                try {
                    // Isolation always runs (even when network sync is disabled).
                    ensureAccountOwnership(userId)
                    activeUserId.set(userId)
                    stateStore.setLastSyncedUserId(userId)

                    if (!featureGate.isEnabled()) {
                        loggingPort.info(TAG, "cloud sync disabled by feature gate; ownership only")
                        _syncStatus.value = SyncStatus.Idle
                        markSessionReady(userId)
                        return@withLock CloudSyncOutcome.Success
                    }

                    // Local-first: unlock shell when this account already has hydrated data.
                    localAlreadyUsable = stateStore.isHydrated(userId)
                    if (localAlreadyUsable) {
                        markSessionReady(userId)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    activeUserId.set(null)
                    _syncStatus.value = SyncStatus.Error(error.message ?: "sync start failed")
                    _sessionState.value =
                        CloudSessionState.Failed(
                            userId = userId,
                            message = error.message ?: MSG_SESSION_FAILED,
                        )
                    return@withLock CloudSyncOutcome.Failed(
                        listOf(CloudSyncDiagnostic.OperationFailed(error)),
                    )
                }

                try {
                    runPushPull(userId)
                    markSessionReady(userId)
                    CloudSyncOutcome.Success
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    _syncStatus.value = SyncStatus.Error(error.message ?: "sync start failed")
                    try {
                        cloudSyncScheduler.enqueueSoon()
                    } catch (scheduleError: Throwable) {
                        loggingPort.error(
                            TAG,
                            "start recovery enqueue failed: ${scheduleError.message}",
                            scheduleError,
                        )
                    }
                    if (localAlreadyUsable || _sessionState.value is CloudSessionState.Ready) {
                        // Keep shell open; background worker retries residual/network work.
                        // Thermos round 1 (A2): this swallow-to-Success branch
                        // has no Failed outcome for a boundary to drain, so —
                        // like the B12 recovery-enqueue record above, which
                        // also has no outcome path — the adapter records this
                        // failure itself, exactly once, in the legacy shape.
                        // Every other start failure returns Failed unrecorded
                        // here; the boundary drains own those.
                        loggingPort.error(
                            TAG,
                            "start push/pull failed: ${error.message}",
                            error,
                        )
                        return@withLock CloudSyncOutcome.Success
                    }
                    _sessionState.value =
                        CloudSessionState.Failed(
                            userId = userId,
                            message = error.message ?: MSG_SESSION_FAILED,
                        )
                    CloudSyncOutcome.Failed(
                        listOf(CloudSyncDiagnostic.OperationFailed(error)),
                    )
                }
            }
        }

    override suspend fun stop() {
        withContext(ioDispatcher) {
            mutex.withLock {
                // Session teardown only — keep durable hydrate/cursor for same-user re-login.
                activeUserId.set(null)
                cloudSyncScheduler.cancel()
                _sessionState.value = CloudSessionState.Inactive
                _syncStatus.value = SyncStatus.Idle
            }
        }
    }

    override suspend fun requestSync(): CloudSyncOutcome {
        if (!featureGate.isEnabled()) {
            return CloudSyncOutcome.Success
        }
        return withContext(ioDispatcher) {
            mutex.withLock {
                val userId = activeUserId.get()
                    ?: return@withLock CloudSyncOutcome.Failed(
                        listOf(
                            CloudSyncDiagnostic.OperationFailed(CloudSyncNotStartedException()),
                        ),
                    )
                try {
                    runPushPull(userId)
                    markSessionReady(userId)
                    CloudSyncOutcome.Success
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    _syncStatus.value = SyncStatus.Error(error.message ?: "request sync failed")
                    // Do not enqueueSoon(REPLACE) here. Residual failures retry via WorkManager
                    // backoff; other failures give up after bounded retries. UI/scheduler retries
                    // must enqueue outside the running worker — REPLACE unique work from inside
                    // doWork can self-cancel.
                    CloudSyncOutcome.Failed(
                        listOf(CloudSyncDiagnostic.OperationFailed(error)),
                    )
                }
            }
        }
    }

    override suspend fun endSessionAndWipe(userId: String): CloudSyncOutcome =
        withContext(ioDispatcher) {
            mutex.withLock {
                try {
                    loggingPort.info(TAG, "endSessionAndWipe")
                    activeUserId.set(null)
                    cloudSyncScheduler.cancel()
                    writeCoordinator.withWriteLock {
                        userOwnedDataClearer.clearUserOwnedData()
                        // Keep lastSyncedUserId so the next start can still detect a switch if
                        // any user rows survived this wipe.
                        stateStore.clearHydrateState(userId)
                    }
                    _sessionState.value = CloudSessionState.Inactive
                    _syncStatus.value = SyncStatus.Idle
                    CloudSyncOutcome.Success
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    CloudSyncOutcome.Failed(
                        listOf(CloudSyncDiagnostic.OperationFailed(error)),
                    )
                }
            }
        }

    /**
     * Canonical push → pull → post-pull residual upload. Shared by [start] and [requestSync].
     * Residual / incomplete pull throws [CloudSyncResidualException] (no Idle claim).
     *
     * On success, rebuilds local-only weekly dashboard tables before Idle so the shell / dashboard
     * can load charts that are not part of the Firestore allowlist.
     */
    private suspend fun runPushPull(userId: String) {
        runSyncPushPullPass(
            awaitSeed = { catalogSeedGate.awaitReady() },
            setStatus = { _syncStatus.value = it },
            isHydrated = { stateStore.isHydrated(userId) },
            uploadUntilIdle = { uploadEngine.uploadUntilIdle(userId).getOrThrow() },
            hasDirtyResidual = { uploadEngine.hasResidualPending() },
            pull = { pullEngine.pull(userId).getOrThrow() },
            afterSuccessfulPull = {
                val rebuildResult = rebuildLocalDerivedTrainingData()
                if (rebuildResult is RebuildLocalDerivedTrainingDataResult.PersistenceFailed) {
                    // Sync itself succeeded; charts may stay empty until the next clean pass
                    // or a local workout finalize (which also refreshes weekly series).
                    // STR-237: the rebuild's stage records ride its outcome;
                    // this adapter is their boundary — record each exactly
                    // once (STR-238: no duplicate breadcrumb on top).
                    rebuildResult.diagnostics.forEach { diagnostic ->
                        when (diagnostic.level) {
                            OperationalDiagnostic.Level.ERROR ->
                                loggingPort.error(
                                    diagnostic.tag,
                                    diagnostic.message,
                                    diagnostic.cause,
                                )
                            OperationalDiagnostic.Level.WARNING ->
                                loggingPort.warning(diagnostic.tag, diagnostic.message)
                        }
                    }
                }
            },
        )
    }

    private fun markSessionReady(userId: String) {
        if (activeUserId.get() == userId) {
            _sessionState.value = CloudSessionState.Ready(userId)
        }
    }

    /**
     * If durable or in-memory owner differs from [userId] (including blank/untrusted owner),
     * wipe Room before any cloud write so User A dirty rows never upload under User B's path.
     *
     * Runs under [UserOwnedWriteCoordinator] so repository writes cannot interleave with wipe.
     */
    private suspend fun ensureAccountOwnership(userId: String) {
        val durableOwner = stateStore.lastSyncedUserId().ifBlank { null }
        val memoryOwner = activeUserId.get()
        if (!AccountOwnershipPolicy.mustWipe(durableOwner, memoryOwner, userId)) {
            return
        }
        val previousOwner = AccountOwnershipPolicy.previousOwner(durableOwner, memoryOwner)
        loggingPort.info(TAG, "account switch or unowned local data; clearing")
        writeCoordinator.withWriteLock {
            userOwnedDataClearer.clearUserOwnedData()
            if (durableOwner != null) {
                stateStore.clearUser(durableOwner)
            } else if (previousOwner != null) {
                stateStore.clearHydrateState(previousOwner)
            }
            // Incoming uid may have stale cursors from a previous visit on this device.
            stateStore.clearHydrateState(userId)
        }
    }

    private companion object {
        const val TAG = CLOUD_SYNC_LOG_TAG
        const val MSG_SESSION_FAILED = "Couldn't load your training data"
    }
}

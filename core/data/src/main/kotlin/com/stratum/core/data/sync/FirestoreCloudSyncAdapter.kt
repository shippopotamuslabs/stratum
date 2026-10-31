package com.stratum.core.data.sync
import com.stratum.core.infrastructure.sync.CloudSyncResidualException
import com.stratum.core.infrastructure.sync.UserOwnedWriteCoordinator
import com.stratum.core.infrastructure.sync.CloudSyncFeatureGate

import com.stratum.core.infrastructure.work.CloudSyncNotStartedException
import com.stratum.core.infrastructure.work.CloudSyncScheduler
import com.stratum.domain.di.IoDispatcher
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.sync.CloudSessionState
import com.stratum.application.sync.CloudSyncPort
import com.stratum.domain.sync.SyncStatus
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
 * Residual work throws [CloudSyncResidualException] so [requestSync] returns failure and
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

    override suspend fun start(userId: String): Result<Unit> {
        return withContext(ioDispatcher) {
            mutex.withLock {
                _sessionState.value = CloudSessionState.Establishing(userId)
                try {
                    // Isolation always runs (even when network sync is disabled).
                    ensureAccountOwnership(userId)
                    activeUserId.set(userId)
                    stateStore.setLastSyncedUserId(userId)

                    if (!featureGate.isEnabled()) {
                        loggingPort.info(TAG, "cloud sync disabled by feature gate; ownership only")
                        _syncStatus.value = SyncStatus.Idle
                        markSessionReady(userId)
                        return@withLock Result.success(Unit)
                    }

                    // Local-first: unlock shell when this account already has hydrated data.
                    val localAlreadyUsable = stateStore.isHydrated(userId)
                    if (localAlreadyUsable) {
                        markSessionReady(userId)
                    }

                    try {
                        runPushPull(userId)
                        markSessionReady(userId)
                        Result.success(Unit)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        loggingPort.error(TAG, "start push/pull failed: ${error.message}", error)
                        _syncStatus.value = SyncStatus.Error(error.message ?: "sync start failed")
                        runCatching { cloudSyncScheduler.enqueueSoon() }
                        if (localAlreadyUsable || _sessionState.value is CloudSessionState.Ready) {
                            // Keep shell open; background worker retries residual/network work.
                            Result.success(Unit)
                        } else {
                            _sessionState.value =
                                CloudSessionState.Failed(
                                    userId = userId,
                                    message = error.message ?: MSG_SESSION_FAILED,
                                )
                            Result.failure(error)
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    loggingPort.error(TAG, "start failed: ${error.message}", error)
                    activeUserId.set(null)
                    _syncStatus.value = SyncStatus.Error(error.message ?: "sync start failed")
                    _sessionState.value =
                        CloudSessionState.Failed(
                            userId = userId,
                            message = error.message ?: MSG_SESSION_FAILED,
                        )
                    Result.failure(error)
                }
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

    override suspend fun requestSync(): Result<Unit> {
        if (!featureGate.isEnabled()) {
            return Result.success(Unit)
        }
        return withContext(ioDispatcher) {
            mutex.withLock {
                val userId = activeUserId.get()
                    ?: return@withLock Result.failure(CloudSyncNotStartedException())
                try {
                    runPushPull(userId)
                    markSessionReady(userId)
                    Result.success(Unit)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    loggingPort.error(TAG, "requestSync failed: ${error.message}", error)
                    _syncStatus.value = SyncStatus.Error(error.message ?: "request sync failed")
                    // Do not enqueueSoon(REPLACE) here. Residual failures retry via WorkManager
                    // backoff; other failures give up after bounded retries. UI/scheduler retries
                    // must enqueue outside the running worker — REPLACE unique work from inside
                    // doWork can self-cancel.
                    Result.failure(error)
                }
            }
        }
    }

    override suspend fun endSessionAndWipe(userId: String): Result<Unit> {
        return withContext(ioDispatcher) {
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
                    Result.success(Unit)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    loggingPort.error(TAG, "endSessionAndWipe failed: ${error.message}", error)
                    Result.failure(error)
                }
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
            logError = { msg -> loggingPort.error(TAG, msg) },
            afterSuccessfulPull = {
                rebuildLocalDerivedTrainingData()
                    .onFailure { error ->
                        // Sync itself succeeded; charts may stay empty until the next clean pass
                        // or a local workout finalize (which also refreshes weekly series).
                        loggingPort.error(
                            TAG,
                            "post-pull derived rebuild failed: ${error.message}",
                            error,
                        )
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
        const val TAG = "FirestoreCloudSync"
        const val MSG_SESSION_FAILED = "Couldn't load your training data"
    }
}

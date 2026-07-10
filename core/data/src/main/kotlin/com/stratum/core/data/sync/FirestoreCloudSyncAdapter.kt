package com.stratum.core.data.sync

import com.stratum.core.data.work.CloudSyncNotStartedException
import com.stratum.core.data.work.CloudSyncScheduler
import com.stratum.domain.di.IoDispatcher
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.sync.CloudSyncPort
import com.stratum.domain.sync.SyncStatus
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
 * so same-user re-login can incremental-pull.
 *
 * Adapter mutex serializes [start]/[stop]/[requestSync] against each other only — Room write
 * isolation is [UserOwnedWriteCoordinator] (repos + remote apply/adopt).
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
    private val loggingPort: LoggingPort,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CloudSyncPort {
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val activeUserId = AtomicReference<String?>(null)
    private val mutex = Mutex()

    override suspend fun start(userId: String): Result<Unit> {
        return withContext(ioDispatcher) {
            mutex.withLock {
                runCatching {
                    // Isolation always runs (even when network sync is disabled).
                    ensureAccountOwnership(userId)
                    activeUserId.set(userId)
                    stateStore.setLastSyncedUserId(userId)

                    if (!featureGate.isEnabled()) {
                        loggingPort.info(TAG, "cloud sync disabled by feature gate; ownership only")
                        _syncStatus.value = SyncStatus.Idle
                        return@runCatching
                    }

                    runPushPull(userId)
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    loggingPort.error(TAG, "start failed: ${error.message}", error)
                    _syncStatus.value = SyncStatus.Error(error.message ?: "sync start failed")
                    // start() is not worker-driven — schedule a follow-up pass for residual/network fails.
                    if (featureGate.isEnabled()) {
                        runCatching { cloudSyncScheduler.enqueueSoon() }
                    }
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
                runCatching {
                    runPushPull(userId)
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    loggingPort.error(TAG, "requestSync failed: ${error.message}", error)
                    _syncStatus.value = SyncStatus.Error(error.message ?: "request sync failed")
                    // Do not enqueueSoon(REPLACE) here — CloudSyncWorker maps failure → Result.retry()
                    // with backoff. REPLACE unique work from inside the running worker can self-cancel.
                }
            }
        }
    }

    /**
     * Canonical push → pull → post-pull residual upload. Shared by [start] and [requestSync].
     * Residual / incomplete pull throws [CloudSyncResidualException] (no Idle claim).
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
        )
    }

    /**
     * If durable or in-memory owner differs from [userId], wipe Room before any cloud write
     * so User A dirty rows never upload under User B's path.
     *
     * Runs under [UserOwnedWriteCoordinator] so repository writes cannot interleave with wipe.
     */
    private suspend fun ensureAccountOwnership(userId: String) {
        val durableOwner = stateStore.lastSyncedUserId().ifBlank { null }
        val memoryOwner = activeUserId.get()
        val previousOwner = durableOwner ?: memoryOwner
        if (previousOwner != null && previousOwner != userId) {
            loggingPort.info(TAG, "account switch $previousOwner → $userId; clearing local data")
            writeCoordinator.withWriteLock {
                userOwnedDataClearer.clearForAccountSwitch()
                stateStore.clearUser(previousOwner)
            }
        }
    }

    private companion object {
        const val TAG = "FirestoreCloudSync"
    }
}

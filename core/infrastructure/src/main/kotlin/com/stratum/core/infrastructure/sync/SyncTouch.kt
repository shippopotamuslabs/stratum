package com.stratum.core.infrastructure.sync
import com.stratum.core.infrastructure.sync.SyncFlags

import com.stratum.core.infrastructure.work.CloudSyncScheduler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single write-side entry for syncable Room mutations:
 * - stamps dirty / delete outbox
 * - schedules upload when the feature gate is on
 * - serializes with account-switch wipes via [UserOwnedWriteCoordinator]
 *
 * Every repository mutation that stamps [SyncFlags.PENDING_UPSERT] must call [markDirty]
 * (or enqueue a delete, which notifies internally). Prefer wrapping the DAO write in
 * [withWriteLock] / [mutate] so wipe cannot interleave.
 */
@Singleton
class SyncTouch @Inject constructor(
    private val cloudDeleteQueue: CloudDeleteQueue,
    private val cloudSyncScheduler: CloudSyncScheduler,
    private val featureGate: CloudSyncFeatureGate,
    private val writeCoordinator: UserOwnedWriteCoordinator,
) {
    /** Run a user-owned write under the same lock as account switch wipe. */
    suspend fun <T> withWriteLock(block: suspend () -> T): T = writeCoordinator.withWriteLock(block)

    /**
     * Lock + work + [markDirty] ritual for pure local mutations (no return-value branching).
     * Prefer this when the write always dirties; use [withWriteLock] when the caller needs
     * fine-grained control (conditional dirty, mixed delete/upsert).
     */
    suspend fun <T> mutate(block: suspend () -> T): T =
        withWriteLock {
            val result = block()
            markDirty()
            result
        }

    fun markDirty() {
        if (!featureGate.isEnabled()) return
        runCatching { cloudSyncScheduler.enqueueSoon() }
    }

    suspend fun replaceIds(
        collection: SyncCollection,
        previousIds: Collection<String>,
        nextIds: Collection<String>,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        cloudDeleteQueue.replaceIds(collection, previousIds, nextIds, nowMillis)
        markDirty()
    }

    suspend fun enqueueDelete(
        collection: SyncCollection,
        entityIds: Collection<String>,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        cloudDeleteQueue.enqueue(collection, entityIds, nowMillis)
        if (entityIds.isNotEmpty()) markDirty()
    }

    suspend fun enqueueDelete(
        collection: SyncCollection,
        entityId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        enqueueDelete(collection, listOf(entityId), nowMillis)
    }

    suspend fun cancelDelete(
        collection: SyncCollection,
        entityIds: Collection<String>,
    ) {
        cloudDeleteQueue.cancel(collection, entityIds)
    }

    suspend fun cancelDelete(
        collection: SyncCollection,
        entityId: String,
    ) {
        cloudDeleteQueue.cancel(collection, entityId)
    }
}

package com.stratum.core.data.sync

import com.stratum.core.infrastructure.db.PendingCloudDeleteDao
import com.stratum.core.infrastructure.db.PendingCloudDeleteEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues deleted entity document ids for later cloud tombstone upload.
 * Re-upserts cancel pending deletes for the same id so recreate races cannot wipe cloud.
 *
 * Write-side helper used by [SyncTouch]; repositories should call [SyncTouch] only.
 */
@Singleton
class CloudDeleteQueue @Inject constructor(
    private val dao: PendingCloudDeleteDao,
) {
    suspend fun enqueue(
        collection: SyncCollection,
        entityIds: Collection<String>,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (entityIds.isEmpty()) return
        val rows =
            entityIds.distinct().map { id ->
                PendingCloudDeleteEntity(
                    rowId = PendingCloudDeleteEntity.rowId(collection.firestorePath, id),
                    collectionPath = collection.firestorePath,
                    entityId = id,
                    enqueuedAtEpochMillis = nowMillis,
                )
            }
        dao.upsertAll(rows)
    }

    suspend fun enqueue(collection: SyncCollection, entityId: String, nowMillis: Long = System.currentTimeMillis()) {
        enqueue(collection, listOf(entityId), nowMillis)
    }

    /** Call when a local upsert re-creates an id that may still be in the delete outbox. */
    suspend fun cancel(collection: SyncCollection, entityIds: Collection<String>) {
        if (entityIds.isEmpty()) return
        dao.cancelAll(collection.firestorePath, entityIds.distinct())
    }

    suspend fun cancel(collection: SyncCollection, entityId: String) {
        dao.cancel(collection.firestorePath, entityId)
    }

    /**
     * Diff previous vs next id sets: enqueue removals for cloud delete, cancel outbox for keeps.
     * Canonical helper for replace-all / hierarchy rewrite write paths.
     */
    suspend fun replaceIds(
        collection: SyncCollection,
        previousIds: Collection<String>,
        nextIds: Collection<String>,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val previous = previousIds.toSet()
        val next = nextIds.toSet()
        enqueue(collection, previous - next, nowMillis)
        cancel(collection, next)
    }
}

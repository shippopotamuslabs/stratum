package com.stratum.core.infrastructure.sync.handlers

import com.stratum.core.infrastructure.sync.LocalSyncMeta
import com.stratum.core.infrastructure.sync.SyncCollection

/**
 * One registration per allowlisted Firestore collection.
 * Engines only iterate [SyncHandlerRegistry] — add a collection by implementing this once.
 */
interface SyncCollectionHandler {
    val collection: SyncCollection

    /** Dirty local rows ready to upload (bounded). */
    suspend fun pendingRows(limit: Int = 500): List<PendingSyncRow>

    /**
     * Cheap existence check — prefer a dedicated probe that avoids full encode.
     * Default loads one pending row (including encode) when no probe is provided.
     */
    suspend fun hasPending(): Boolean = pendingRows(limit = 1).isNotEmpty()

    suspend fun localMeta(id: String): LocalSyncMeta?

    suspend fun deleteLocal(id: String)

    /** Clear dirty only if the row was not edited after [updatedAt]. */
    suspend fun markSyncedIfUnchanged(id: String, updatedAt: Long)

    /**
     * After confirmed LWW loss when remote cannot be adopted: drop local dirty so upload
     * does not infinite-loop. Prefer re-pull later over stuck PENDING.
     */
    suspend fun forceCleanLocal(id: String) {
        val meta = localMeta(id) ?: return
        markSyncedIfUnchanged(id, meta.updatedAtEpochMillis)
    }

    /**
     * Decode remote document and upsert into Room as CLEAN.
     * @return false if required fields are missing/corrupt (caller should not advance cursor).
     */
    suspend fun upsertFromRemote(id: String, data: Map<String, Any?>): Boolean
}

/** Snapshot of a dirty Room row for Firestore upload. */
data class PendingSyncRow(
    val id: String,
    val updatedAtEpochMillis: Long,
    val payload: Map<String, Any?>,
)

/** Result of applying a single remote document (cursor / retry semantics). */
enum class RemoteApplyOutcome {
    Applied,
    Skipped,
    Failed,
}

/**
 * Table-driven handler: engines stay generic; collections are registered as specs.
 *
 * @param checkHasPending optional cheap probe (e.g. `pending(1).isNotEmpty()` without encode).
 */
class GenericSyncHandler(
    override val collection: SyncCollection,
    private val loadPending: suspend (Int) -> List<PendingSyncRow>,
    private val loadLocalMeta: suspend (String) -> LocalSyncMeta?,
    private val deleteById: suspend (String) -> Unit,
    private val markSynced: suspend (String, Long) -> Unit,
    private val upsertRemote: suspend (String, Map<String, Any?>) -> Boolean,
    private val checkHasPending: (suspend () -> Boolean)? = null,
) : SyncCollectionHandler {
    override suspend fun pendingRows(limit: Int): List<PendingSyncRow> = loadPending(limit)

    override suspend fun hasPending(): Boolean =
        checkHasPending?.invoke() ?: pendingRows(limit = 1).isNotEmpty()

    override suspend fun localMeta(id: String): LocalSyncMeta? = loadLocalMeta(id)

    override suspend fun deleteLocal(id: String) = deleteById(id)

    override suspend fun markSyncedIfUnchanged(id: String, updatedAt: Long) =
        markSynced(id, updatedAt)

    override suspend fun upsertFromRemote(id: String, data: Map<String, Any?>): Boolean =
        upsertRemote(id, data)
}

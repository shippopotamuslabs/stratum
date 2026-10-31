package com.stratum.core.infrastructure.sync
import com.stratum.core.infrastructure.sync.UserOwnedWriteCoordinator
import com.stratum.core.infrastructure.sync.SyncCollection

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.stratum.core.infrastructure.db.PendingCloudDeleteDao
import com.stratum.core.infrastructure.db.PendingCloudDeleteEntity
import com.stratum.core.infrastructure.sync.handlers.SyncHandlerRegistry
import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Uploads dirty Room rows and pure tombstone documents via [SyncHandlerRegistry].
 *
 * Upserts and tombstones use a conditional LWW transaction so a dirty local clock cannot
 * permanently clobber a newer remote document. On LWW loss the remote snapshot is adopted
 * locally (under [UserOwnedWriteCoordinator]); adopt failure force-cleans local dirty so
 * upload cannot infinite-loop on a poison remote.
 *
 * @return true if more pending work may remain after this pass (caller should re-pass).
 */
@Singleton
class FirestorePendingUploadEngine @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val stateStore: FirestoreSyncStateStore,
    private val handlers: SyncHandlerRegistry,
    private val pendingCloudDeleteDao: PendingCloudDeleteDao,
    private val writeCoordinator: UserOwnedWriteCoordinator,
    private val loggingPort: LoggingPort,
) {
    /**
     * @return true when residual dirty rows or deletes may remain (caller should re-pass).
     */
    suspend fun uploadPending(userId: String): Result<Boolean> =
        try {
            val deviceId = stateStore.deviceId()
            val writtenAt = stateStore.currentTimeMillis()
            var hitLimit = false

            for (handler in handlers.inUploadOrder()) {
                val rows = handler.pendingRows(limit = PENDING_ROW_LIMIT)
                if (rows.size >= PENDING_ROW_LIMIT) hitLimit = true
                for (row in rows) {
                    val envelope =
                        FirestoreSyncMetadata.envelope(
                            deviceId = deviceId,
                            clientMutationId = stateStore.newClientMutationId(),
                            writtenAt = writtenAt,
                            updatedAt = row.updatedAtEpochMillis,
                            deleted = false,
                        )
                    val payload = row.payload + envelope
                    val path = docPath(userId, handler.collection, row.id)
                    when (
                        val write =
                            conditionalWrite(
                                path = path,
                                data = payload,
                                localUpdatedAt = row.updatedAtEpochMillis,
                                localDeviceId = deviceId,
                                pureOverwrite = false,
                            )
                    ) {
                        ConditionalWrite.Written -> {
                            writeCoordinator.withWriteLock {
                                handler.markSyncedIfUnchanged(row.id, row.updatedAtEpochMillis)
                            }
                        }
                        is ConditionalWrite.LostToRemote -> {
                            adoptRemote(handler.collection, row.id, write.remoteData)
                        }
                    }
                }
            }

            val deletesRemaining = flushTombstones(userId, deviceId, writtenAt)
            // Residual: limit hit, tombstones left, or cheap dirty existence (no encode).
            // Cheap hasPending covers concurrent edits that failed markSyncedIfUnchanged CAS.
            Result.success(
                hitLimit || deletesRemaining || handlers.inUploadOrder().any { it.hasPending() },
            )
        } catch (error: kotlinx.coroutines.CancellationException) {
            // WorkManager REPLACE cancels in-flight workers — not an upload failure.
            throw error
        } catch (error: Exception) {
            loggingPort.error(TAG, "uploadPending failed: ${error.message}", error)
            Result.failure(error)
        }

    /**
     * Multi-pass upload until idle or [maxPasses] exhausted.
     * @return true if work may still remain after the last pass.
     */
    suspend fun uploadUntilIdle(userId: String, maxPasses: Int = MAX_UPLOAD_PASSES): Result<Boolean> {
        var remaining = true
        var pass = 0
        while (remaining && pass < maxPasses) {
            remaining = uploadPending(userId).getOrElse { return Result.failure(it) }
            pass++
        }
        return Result.success(remaining)
    }

    /**
     * Cheap residual probe: any collection has dirty rows or the delete outbox is non-empty.
     * Used after pull so demotion side-effects can trigger a same-pass second upload.
     */
    suspend fun hasResidualPending(): Boolean =
        pendingCloudDeleteDao.count() > 0 || handlers.inUploadOrder().any { it.hasPending() }

    private sealed class ConditionalWrite {
        data object Written : ConditionalWrite()
        data class LostToRemote(val remoteData: Map<String, Any?>) : ConditionalWrite()
    }

    /**
     * @param pureOverwrite when true (tombstones), use set without merge so PII fields are wiped.
     */
    private suspend fun conditionalWrite(
        path: String,
        data: Map<String, Any?>,
        localUpdatedAt: Long,
        localDeviceId: String,
        pureOverwrite: Boolean,
    ): ConditionalWrite {
        val ref = firestore.document(path)
        return firestore
            .runTransaction { tx ->
                val snap = tx.get(ref)
                if (snap.exists()) {
                    val remoteData = snap.data.orEmpty()
                    val remoteAt =
                        (remoteData[FirestoreSyncMetadata.UPDATED_AT] as? Number)?.toLong() ?: 0L
                    val remoteDevice = remoteData[FirestoreSyncMetadata.DEVICE_ID] as? String
                    val localWins =
                        FirestoreSyncRemoteApplyPolicy.localWinsLww(
                            localUpdatedAt = localUpdatedAt,
                            remoteUpdatedAt = remoteAt,
                            localDeviceId = localDeviceId,
                            remoteDeviceId = remoteDevice,
                        )
                    if (!localWins) {
                        return@runTransaction ConditionalWrite.LostToRemote(remoteData)
                    }
                }
                if (pureOverwrite) {
                    tx.set(ref, data)
                } else {
                    tx.set(ref, data, SetOptions.merge())
                }
                ConditionalWrite.Written
            }.await()
    }

    private suspend fun adoptRemote(
        collection: SyncCollection,
        id: String,
        remoteData: Map<String, Any?>,
    ) {
        val handler = handlers.get(collection)
        val ok =
            runLwwAdopt(
                onError = { e ->
                    loggingPort.error(
                        TAG,
                        "LWW-loss adopt failed for ${collection.firestorePath}/$id: ${e.message}",
                        e,
                    )
                },
            ) {
                writeCoordinator.withWriteLock {
                    if (FirestoreSyncMetadata.isDeleted(remoteData)) {
                        handler.deleteLocal(id)
                        true
                    } else {
                        handler.upsertFromRemote(id, remoteData)
                    }
                }
            }
        if (!ok) {
            // Confirmed LWW loss + unadoptable remote → force-clean local dirty to break
            // infinite lose-loop. Next pull may still fail on poison docs (cursor stops).
            loggingPort.error(
                TAG,
                "LWW-loss adopt decode failed for ${collection.firestorePath}/$id; force-cleaning local dirty",
            )
            writeCoordinator.withWriteLock {
                handler.forceCleanLocal(id)
            }
        }
    }

    /**
     * Flush tombstones with delete-time LWW clock ([PendingCloudDeleteEntity.enqueuedAtEpochMillis]).
     * Conditional transactions; unconditional batch.set is forbidden.
     *
     * @return true if more tombstones remain in the outbox.
     */
    private suspend fun flushTombstones(userId: String, deviceId: String, writtenAt: Long): Boolean {
        val pending = pendingCloudDeleteDao.peekPending(BATCH_SIZE)
        if (pending.isEmpty()) return false
        val completed = mutableListOf<String>()
        for (row in pending) {
            when (flushOneTombstone(userId, deviceId, writtenAt, row)) {
                TombstoneOutcome.DropOutbox, TombstoneOutcome.Written -> completed.add(row.rowId)
                TombstoneOutcome.SkipKeepOutbox -> Unit
            }
        }
        if (completed.isNotEmpty()) {
            pendingCloudDeleteDao.deleteByIds(completed)
        }
        // Residual if we hit the batch cap or outbox still non-empty.
        return pending.size >= BATCH_SIZE || pendingCloudDeleteDao.count() > 0
    }

    private enum class TombstoneOutcome {
        Written,
        DropOutbox,
        SkipKeepOutbox,
    }

    private suspend fun flushOneTombstone(
        userId: String,
        deviceId: String,
        writtenAt: Long,
        row: PendingCloudDeleteEntity,
    ): TombstoneOutcome {
        val handler = handlers.forFirestorePath(row.collectionPath) ?: return TombstoneOutcome.DropOutbox
        // Recreate race: local row exists again → drop outbox entry, do not tombstone.
        val localStillExists =
            writeCoordinator.withWriteLock {
                handler.localMeta(row.entityId) != null
            }
        if (localStillExists) {
            return TombstoneOutcome.DropOutbox
        }
        val deleteClock = row.enqueuedAtEpochMillis
        val path = "users/$userId/${row.collectionPath}/${row.entityId}"
        val data =
            FirestoreSyncMetadata.envelope(
                deviceId = deviceId,
                clientMutationId = stateStore.newClientMutationId(),
                writtenAt = writtenAt,
                updatedAt = deleteClock,
                deleted = true,
            )
        return when (
            val write =
                conditionalWrite(
                    path = path,
                    data = data,
                    localUpdatedAt = deleteClock,
                    localDeviceId = deviceId,
                    pureOverwrite = true,
                )
        ) {
            ConditionalWrite.Written -> TombstoneOutcome.Written
            is ConditionalWrite.LostToRemote -> {
                // Remote is newer — drop outbox and adopt remote (recreate/edit wins over delete).
                adoptRemote(handler.collection, row.entityId, write.remoteData)
                TombstoneOutcome.DropOutbox
            }
        }
    }

    private fun docPath(userId: String, collection: SyncCollection, id: String): String =
        "users/$userId/${collection.firestorePath}/$id"

    private companion object {
        const val TAG = "FirestoreUpload"
        const val BATCH_SIZE = 450
        const val PENDING_ROW_LIMIT = 500
        const val MAX_UPLOAD_PASSES = 8
    }
}

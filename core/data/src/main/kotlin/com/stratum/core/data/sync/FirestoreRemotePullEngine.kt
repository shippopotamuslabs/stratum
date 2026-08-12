package com.stratum.core.data.sync

import android.database.sqlite.SQLiteConstraintException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.stratum.core.data.sync.handlers.RemoteApplyOutcome
import com.stratum.core.data.sync.handlers.SyncCollectionHandler
import com.stratum.core.data.sync.handlers.SyncHandlerRegistry
import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Pulls allowlisted collections via [SyncHandlerRegistry] in dependency order.
 * Every document uses [FirestoreSyncRemoteApplyPolicy.decide]; tombstones delete local rows.
 *
 * Single pull API: always resumes from per-collection composite cursors `(updated_at, docId)`
 * (empty/zero when unset). [FirestoreSyncStateStore.isHydrated] is a pure "first clean
 * zero-failure pass completed" marker — not a pull mode switch.
 *
 * Within a collection the cursor advances only through successfully processed documents;
 * the first [RemoteApplyOutcome.Failed] stops paging that collection so equal-timestamp
 * docs after a failure are never skipped.
 *
 * Room mutations run under [UserOwnedWriteCoordinator] (not held across network I/O).
 */
@Singleton
class FirestoreRemotePullEngine @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val stateStore: FirestoreSyncStateStore,
    private val handlers: SyncHandlerRegistry,
    private val writeCoordinator: UserOwnedWriteCoordinator,
    private val loggingPort: LoggingPort,
) {
    /**
     * Ordered per-collection pull using stored cursors.
     *
     * @return [PullResult] with failure/hydrate flags so the adapter never claims Idle on residual.
     * Marks hydrated after a clean pass with zero apply failures (first time or recovery).
     */
    suspend fun pull(userId: String): Result<PullResult> =
        try {
            val deviceId = stateStore.deviceId()
            var anyFailures = false
            val failedCollections = mutableSetOf<SyncCollection>()
            for (handler in handlers.inHydrateOrder()) {
                if (handler.collection.hydrateParents.any { it in failedCollections }) {
                    loggingPort.info(
                        TAG,
                        "skip ${handler.collection.firestorePath}; parent pull incomplete",
                    )
                    anyFailures = true
                    continue
                }
                val since = stateStore.lastPullCursor(userId, handler.collection)
                val result = pullCollection(userId, handler.collection, deviceId, since)
                if (result.hadFailures) {
                    anyFailures = true
                    failedCollections.add(handler.collection)
                }
                if (result.cursorAdvanced) {
                    stateStore.setLastPullCursor(userId, handler.collection, result.nextCursor)
                }
            }
            if (!anyFailures) {
                stateStore.setHydrated(userId, true)
            } else if (!stateStore.isHydrated(userId)) {
                loggingPort.error(TAG, "pull incomplete; will retry failed docs before hydrated")
            }
            Result.success(
                PullResult(
                    hadFailures = anyFailures,
                    hydrated = stateStore.isHydrated(userId),
                ),
            )
        } catch (error: kotlinx.coroutines.CancellationException) {
            // WorkManager REPLACE / stop cancels in-flight CloudSyncWorker coroutines — not a pull
            // failure. Never log CancellationException as error (runCatching would also swallow it
            // incorrectly into Result.failure).
            throw error
        } catch (error: Exception) {
            loggingPort.error(TAG, "pull failed: ${error.message}", error)
            Result.failure(error)
        }

    private data class PullCollectionResult(
        val nextCursor: PullCursorRecord,
        val cursorAdvanced: Boolean,
        val hadFailures: Boolean,
    )

    private suspend fun pullCollection(
        userId: String,
        collection: SyncCollection,
        localDeviceId: String,
        since: PullCursorRecord,
    ): PullCollectionResult {
        val handler = handlers.get(collection)
        val collectionRef = firestore.collection("users/$userId/${collection.firestorePath}")
        var resumeAfter: DocumentSnapshot? = null
        var safeCursor = since
        var cursorAdvanced = false
        var hadFailures = false
        var morePages = true

        while (morePages) {
            val page = fetchPage(collectionRef, since, resumeAfter)
            if (page.isEmpty()) {
                morePages = false
            } else {
                val pageResult = applyPage(handler, collection, page, localDeviceId)
                if (pageResult.lastSafeDoc != null) {
                    safeCursor =
                        PullCursorRecord(
                            updatedAtEpochMillis =
                                (pageResult.lastSafeDoc.data?.get(FirestoreSyncMetadata.UPDATED_AT)
                                    as? Number)?.toLong()
                                    ?: safeCursor.updatedAtEpochMillis,
                            documentId = pageResult.lastSafeDoc.id,
                        )
                    cursorAdvanced = true
                }
                if (pageResult.hadFailures) {
                    hadFailures = true
                    // Stop paging this collection — cursor frozen at last successful doc.
                    morePages = false
                } else {
                    resumeAfter = page.last()
                    morePages = page.size >= PAGE_SIZE
                }
            }
        }

        return PullCollectionResult(
            nextCursor = safeCursor,
            cursorAdvanced = cursorAdvanced,
            hadFailures = hadFailures,
        )
    }

    private data class PageApplyResult(
        /** Last Applied/Skipped document in this page (for composite cursor). */
        val lastSafeDoc: DocumentSnapshot?,
        val hadFailures: Boolean,
    )

    private suspend fun fetchPage(
        collectionRef: com.google.firebase.firestore.CollectionReference,
        since: PullCursorRecord,
        lastDoc: DocumentSnapshot?,
    ): List<DocumentSnapshot> {
        var query: Query =
            collectionRef
                .orderBy(FirestoreSyncMetadata.UPDATED_AT)
                .orderBy(FieldPath.documentId())
                .limit(PAGE_SIZE.toLong())
        when {
            lastDoc != null -> {
                // In-session paging after a successful page.
                query = query.startAfter(lastDoc)
            }
            since.isEmpty() -> {
                // Full scan from the beginning.
            }
            else -> {
                query =
                    query.startAfter(since.updatedAtEpochMillis, since.documentId)
            }
        }
        val snapshot = query.get().await()
        return snapshot.documents
    }

    private suspend fun applyPage(
        handler: SyncCollectionHandler,
        collection: SyncCollection,
        docs: List<DocumentSnapshot>,
        localDeviceId: String,
    ): PageApplyResult {
        var lastSafe: DocumentSnapshot? = null
        for (doc in docs) {
            val data = doc.data
            if (data == null) {
                loggingPort.error(TAG, "Missing document data for ${collection.firestorePath}/${doc.id}")
                return PageApplyResult(lastSafeDoc = lastSafe, hadFailures = true)
            }
            val remoteUpdated =
                (data[FirestoreSyncMetadata.UPDATED_AT] as? Number)?.toLong() ?: 0L
            val remoteDevice = data[FirestoreSyncMetadata.DEVICE_ID] as? String
            val outcome =
                applyDocument(
                    handler = handler,
                    id = doc.id,
                    data = data,
                    localDeviceId = localDeviceId,
                    remoteDeviceId = remoteDevice,
                    remoteUpdated = remoteUpdated,
                )
            when (outcome) {
                RemoteApplyOutcome.Applied, RemoteApplyOutcome.Skipped -> {
                    lastSafe = doc
                }
                RemoteApplyOutcome.Failed -> {
                    return PageApplyResult(lastSafeDoc = lastSafe, hadFailures = true)
                }
            }
        }
        return PageApplyResult(lastSafeDoc = lastSafe, hadFailures = false)
    }

    private suspend fun applyDocument(
        handler: SyncCollectionHandler,
        id: String,
        data: Map<String, Any?>,
        localDeviceId: String,
        remoteDeviceId: String?,
        remoteUpdated: Long,
    ): RemoteApplyOutcome {
        return writeCoordinator.withWriteLock {
            val localMeta = handler.localMeta(id)
            val remoteDeleted = FirestoreSyncMetadata.isDeleted(data)
            val decision =
                FirestoreSyncRemoteApplyPolicy.decide(
                    localMeta = localMeta,
                    remoteUpdatedAt = remoteUpdated,
                    remoteDeviceId = remoteDeviceId,
                    localDeviceId = localDeviceId,
                    remoteDeleted = remoteDeleted,
                )
            if (decision == FirestoreSyncRemoteApplyPolicy.Decision.Skip) {
                return@withWriteLock RemoteApplyOutcome.Skipped
            }
            try {
                if (remoteDeleted) {
                    handler.deleteLocal(id)
                    RemoteApplyOutcome.Applied
                } else {
                    val ok = handler.upsertFromRemote(id, data)
                    if (!ok) {
                        loggingPort.error(TAG, "Decode failed for ${handler.collection.firestorePath}/$id")
                        RemoteApplyOutcome.Failed
                    } else {
                        RemoteApplyOutcome.Applied
                    }
                }
            } catch (e: SQLiteConstraintException) {
                loggingPort.error(
                    TAG,
                    "FK/constraint failure for ${handler.collection.firestorePath}/$id: ${e.message}",
                    e,
                )
                RemoteApplyOutcome.Failed
            }
        }
    }

    private companion object {
        const val TAG = "FirestorePull"
        const val PAGE_SIZE = 300
    }
}

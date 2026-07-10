package com.stratum.core.data.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Persists stable [deviceId], durable per-user hydrate/cursor state, and last-account ownership.
 * Session teardown ([FirestoreCloudSyncAdapter.stop]) must not clear durable state.
 */
@Singleton
class FirestoreSyncStateStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store get() = context.cloudSyncPrefsDataStore

    suspend fun deviceId(): String {
        val current = store.data.first()
        if (current.deviceId.isNotBlank()) return current.deviceId
        val generated = UUID.randomUUID().toString()
        store.updateData { prefs ->
            if (prefs.deviceId.isNotBlank()) prefs else prefs.copy(deviceId = generated)
        }
        return store.data.first().deviceId.ifBlank { generated }
    }

    /** Durable account that currently owns local Room data; blank if never set / wiped. */
    suspend fun lastSyncedUserId(): String =
        store.data.first().lastSyncedUserId

    suspend fun setLastSyncedUserId(userId: String) {
        store.updateData { prefs -> prefs.copy(lastSyncedUserId = userId) }
    }

    suspend fun isHydrated(userId: String): Boolean =
        store.data.first().hydratedUserIds[userId] == true

    suspend fun setHydrated(userId: String, hydrated: Boolean) {
        store.updateData { prefs ->
            prefs.copy(hydratedUserIds = prefs.hydratedUserIds + (userId to hydrated))
        }
    }

    /** Per-collection composite pull cursor `(updated_at, documentId)`. */
    suspend fun lastPullCursor(userId: String, collection: SyncCollection): PullCursorRecord {
        val byCollection = store.data.first().collectionPullCursors[userId] ?: return PullCursorRecord.EMPTY
        return byCollection[collection.firestorePath] ?: PullCursorRecord.EMPTY
    }

    suspend fun setLastPullCursor(
        userId: String,
        collection: SyncCollection,
        cursor: PullCursorRecord,
    ) {
        store.updateData { prefs ->
            val userMap = prefs.collectionPullCursors[userId].orEmpty().toMutableMap()
            userMap[collection.firestorePath] = cursor
            prefs.copy(
                collectionPullCursors = prefs.collectionPullCursors + (userId to userMap),
            )
        }
    }

    fun newClientMutationId(): String = UUID.randomUUID().toString()

    fun currentTimeMillis(): Long = System.currentTimeMillis()

    /**
     * Drop hydrate/cursor + account ownership for a user (account switch / explicit wipe only).
     * Do not call on ordinary sign-out — session teardown should keep durable state.
     */
    suspend fun clearUser(userId: String) {
        store.updateData { prefs ->
            prefs.copy(
                lastSyncedUserId =
                    if (prefs.lastSyncedUserId == userId) "" else prefs.lastSyncedUserId,
                hydratedUserIds = prefs.hydratedUserIds - userId,
                collectionPullCursors = prefs.collectionPullCursors - userId,
            )
        }
    }
}

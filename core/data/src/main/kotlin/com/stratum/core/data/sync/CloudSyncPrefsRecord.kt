package com.stratum.core.data.sync

import android.content.Context
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/** Composite pull resume point: (updated_at, documentId) for non-skipping startAfter. */
@Serializable
data class PullCursorRecord(
    val updatedAtEpochMillis: Long = 0L,
    val documentId: String = "",
) {
    fun isEmpty(): Boolean = updatedAtEpochMillis <= 0L && documentId.isEmpty()

    companion object {
        val EMPTY = PullCursorRecord()
    }
}

@Serializable
data class CloudSyncPrefsRecord(
    val deviceId: String = "",
    /**
     * Last Firebase uid whose data may legitimately live in local Room.
     * Used to detect account switches after sign-out (in-memory active uid is cleared).
     */
    val lastSyncedUserId: String = "",
    /** userId → last full hydrate completed */
    val hydratedUserIds: Map<String, Boolean> = emptyMap(),
    /**
     * userId → (collection firestore path → composite cursor).
     * Per-collection so a poison doc in one path cannot freeze unrelated collections.
     */
    val collectionPullCursors: Map<String, Map<String, PullCursorRecord>> = emptyMap(),
)

private val cloudSyncJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

object CloudSyncPrefsRecordSerializer : Serializer<CloudSyncPrefsRecord> {
    override val defaultValue: CloudSyncPrefsRecord = CloudSyncPrefsRecord()

    override suspend fun readFrom(input: InputStream): CloudSyncPrefsRecord {
        val text = input.bufferedReader().use { it.readText() }
        if (text.isBlank()) return defaultValue
        return runCatching {
            cloudSyncJson.decodeFromString(CloudSyncPrefsRecord.serializer(), text)
        }.getOrDefault(defaultValue)
    }

    override suspend fun writeTo(t: CloudSyncPrefsRecord, output: OutputStream) {
        val text = cloudSyncJson.encodeToString(CloudSyncPrefsRecord.serializer(), t)
        output.bufferedWriter().use { it.write(text) }
    }
}

internal val Context.cloudSyncPrefsDataStore by dataStore(
    fileName = "cloud_sync_prefs.json",
    serializer = CloudSyncPrefsRecordSerializer,
)

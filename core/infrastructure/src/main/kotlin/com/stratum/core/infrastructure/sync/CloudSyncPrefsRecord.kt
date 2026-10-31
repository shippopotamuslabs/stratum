package com.stratum.core.infrastructure.sync

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

/**
 * Durable sync truth for this install, persisted as `datastore/cloud_sync_prefs.json`.
 *
 * STR-206: this file is deliberately EXCLUDED from Android Auto Backup and device
 * transfer (see `app/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml`).
 * Restoring it — owner, hydrate flags, pull cursors — alongside an older Room database
 * once made a fresh sign-in trust the stale database as already synced, so newer
 * Firestore state never pulled down. A restore therefore boots with this record at
 * its defaults: blank owner (untrusted → restored Room rows are wiped before the
 * first upload, per [AccountOwnershipPolicy]), no hydrate flags, no cursors (first
 * sign-in full-pulls and converges local state to the server). The Room database
 * deliberately stays in the backup set: without this sync state it is wiped
 * before use, and it remains the only backup copy for never-signed-in local data.
 */
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

/** Backing file name for [CloudSyncPrefsRecord] under `files/datastore/`; backup rules exclude it (STR-206). */
const val CLOUD_SYNC_PREFS_FILE_NAME = "cloud_sync_prefs.json"

internal val Context.cloudSyncPrefsDataStore by dataStore(
    fileName = CLOUD_SYNC_PREFS_FILE_NAME,
    serializer = CloudSyncPrefsRecordSerializer,
)

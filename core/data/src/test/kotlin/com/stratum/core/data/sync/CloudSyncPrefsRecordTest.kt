package com.stratum.core.data.sync

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class CloudSyncPrefsRecordTest {
    @Test
    @DisplayName("composite PullCursorRecord round-trips through serializer")
    fun compositeRoundTrip() = runBlocking {
        val original =
            CloudSyncPrefsRecord(
                deviceId = "d",
                lastSyncedUserId = "u",
                hydratedUserIds = mapOf("u" to true),
                collectionPullCursors =
                    mapOf(
                        "u" to
                            mapOf(
                                "programs" to
                                    PullCursorRecord(
                                        updatedAtEpochMillis = 50L,
                                        documentId = "doc-xyz",
                                    ),
                            ),
                    ),
            )
        val out = ByteArrayOutputStream()
        CloudSyncPrefsRecordSerializer.writeTo(original, out)
        val restored =
            CloudSyncPrefsRecordSerializer.readFrom(ByteArrayInputStream(out.toByteArray()))
        val cursor = restored.collectionPullCursors["u"]?.get("programs")
        assertEquals(50L, cursor?.updatedAtEpochMillis)
        assertEquals("doc-xyz", cursor?.documentId)
        assertEquals("d", restored.deviceId)
        assertEquals("u", restored.lastSyncedUserId)
    }

    @Test
    @DisplayName("default record is the untrusted state a backup-excluded restore boots into (STR-206)")
    fun defaultRecordIsUntrustedPostRestoreState() {
        // backup_rules.xml / data_extraction_rules.xml exclude cloud_sync_prefs.json
        // from Auto Backup and device transfer, so a restored install boots with
        // exactly this record no matter how stale the restored Room database is.
        val fresh = CloudSyncPrefsRecord()
        assertEquals("", fresh.deviceId)
        assertEquals("", fresh.lastSyncedUserId)
        assertTrue(fresh.hydratedUserIds.isEmpty())
        assertTrue(fresh.collectionPullCursors.isEmpty())
        // A blank durable owner is untrusted: the first sign-in wipes any restored
        // Room rows before the first upload and full-pulls from empty cursors, so
        // local state converges to the server instead of trusting the restore.
        assertTrue(
            AccountOwnershipPolicy.mustWipe(
                durableOwner = fresh.lastSyncedUserId.ifBlank { null },
                memoryOwner = null,
                incomingUserId = "uid-any",
            ),
        )
    }

    @Test
    @DisplayName("blank prefs deserialize to defaults")
    fun blankIsDefault() = runBlocking {
        val restored =
            CloudSyncPrefsRecordSerializer.readFrom(ByteArrayInputStream(ByteArray(0)))
        assertEquals(CloudSyncPrefsRecord(), restored)
    }
}

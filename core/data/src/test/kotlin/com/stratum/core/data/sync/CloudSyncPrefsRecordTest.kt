package com.stratum.core.data.sync

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
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
    @DisplayName("blank prefs deserialize to defaults")
    fun blankIsDefault() = runBlocking {
        val restored =
            CloudSyncPrefsRecordSerializer.readFrom(ByteArrayInputStream(ByteArray(0)))
        assertEquals(CloudSyncPrefsRecord(), restored)
    }
}

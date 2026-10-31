package com.stratum.core.infrastructure.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tombstone flush must use delete-time clock ([PendingCloudDeleteEntity.enqueuedAtEpochMillis]),
 * not flush wall clock. These LWW cases mirror that policy.
 */
class TombstoneLwwPolicyTest {
    @Test
    @DisplayName("delete clock older than remote recreate loses LWW — outbox must drop, not overwrite")
    fun olderDeleteLosesToRemoteRecreate() {
        val deleteClock = 1_000L
        val remoteRecreateAt = 2_000L
        assertFalse(
            FirestoreSyncRemoteApplyPolicy.localWinsLww(
                localUpdatedAt = deleteClock,
                remoteUpdatedAt = remoteRecreateAt,
                localDeviceId = "device-a",
                remoteDeviceId = "device-b",
            ),
        )
    }

    @Test
    @DisplayName("delete clock newer than remote wins LWW — pure tombstone may write")
    fun newerDeleteWinsOverStaleRemote() {
        val deleteClock = 3_000L
        val remoteAt = 2_000L
        assertTrue(
            FirestoreSyncRemoteApplyPolicy.localWinsLww(
                localUpdatedAt = deleteClock,
                remoteUpdatedAt = remoteAt,
                localDeviceId = "device-a",
                remoteDeviceId = "device-b",
            ),
        )
    }

    @Test
    @DisplayName("flush wall clock must not be used as delete LWW clock")
    fun flushNowWouldIncorrectlyBeatRemote() {
        val deleteClock = 1_000L
        val remoteAt = 2_000L
        val flushNow = 9_999L
        // Correct: delete-time loses
        assertFalse(
            FirestoreSyncRemoteApplyPolicy.localWinsLww(
                localUpdatedAt = deleteClock,
                remoteUpdatedAt = remoteAt,
                localDeviceId = "a",
                remoteDeviceId = "b",
            ),
        )
        // Incorrect old behavior: flush wall clock would wrongly win
        assertTrue(
            FirestoreSyncRemoteApplyPolicy.localWinsLww(
                localUpdatedAt = flushNow,
                remoteUpdatedAt = remoteAt,
                localDeviceId = "a",
                remoteDeviceId = "b",
            ),
        )
    }

    @Test
    @DisplayName("tombstone envelope carries delete clock as updated_at")
    fun envelopeUsesDeleteClock() {
        val deleteClock = 1_234L
        val envelope =
            FirestoreSyncMetadata.envelope(
                deviceId = "dev",
                clientMutationId = "m1",
                writtenAt = 9_999L,
                updatedAt = deleteClock,
                deleted = true,
            )
        assertTrue(FirestoreSyncMetadata.isDeleted(envelope))
        assertEquals(deleteClock, envelope[FirestoreSyncMetadata.UPDATED_AT])
        // writtenAt is flush-time metadata only — not the LWW clock
        assertEquals(9_999L, envelope[FirestoreSyncMetadata.WRITTEN_AT])
    }

}

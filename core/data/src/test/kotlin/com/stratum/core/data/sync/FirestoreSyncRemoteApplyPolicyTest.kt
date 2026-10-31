package com.stratum.core.data.sync
import com.stratum.core.infrastructure.sync.SyncFlags

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FirestoreSyncRemoteApplyPolicyTest {
    @Test
    fun `absent local always applies`() {
        assertEquals(
            FirestoreSyncRemoteApplyPolicy.Decision.Apply,
            FirestoreSyncRemoteApplyPolicy.decide(
                localMeta = null,
                remoteUpdatedAt = 1L,
                remoteDeviceId = "b",
                localDeviceId = "a",
            ),
        )
    }

    @Test
    fun `dirty local preserves against other device upsert`() {
        assertEquals(
            FirestoreSyncRemoteApplyPolicy.Decision.Skip,
            FirestoreSyncRemoteApplyPolicy.decide(
                localMeta = LocalSyncMeta(SyncFlags.PENDING_UPSERT, 10L),
                remoteUpdatedAt = 99L,
                remoteDeviceId = "device-b",
                localDeviceId = "device-a",
            ),
        )
    }

    @Test
    fun `newer remote tombstone wins over dirty local`() {
        assertEquals(
            FirestoreSyncRemoteApplyPolicy.Decision.Apply,
            FirestoreSyncRemoteApplyPolicy.decide(
                localMeta = LocalSyncMeta(SyncFlags.PENDING_UPSERT, 10L),
                remoteUpdatedAt = 99L,
                remoteDeviceId = "device-b",
                localDeviceId = "device-a",
                remoteDeleted = true,
            ),
        )
    }

    @Test
    fun `older remote tombstone does not beat dirty local`() {
        assertEquals(
            FirestoreSyncRemoteApplyPolicy.Decision.Skip,
            FirestoreSyncRemoteApplyPolicy.decide(
                localMeta = LocalSyncMeta(SyncFlags.PENDING_UPSERT, 100L),
                remoteUpdatedAt = 10L,
                remoteDeviceId = "device-b",
                localDeviceId = "device-a",
                remoteDeleted = true,
            ),
        )
    }

    @Test
    fun `dirty local skips older same-device remote`() {
        assertEquals(
            FirestoreSyncRemoteApplyPolicy.Decision.Skip,
            FirestoreSyncRemoteApplyPolicy.decide(
                localMeta = LocalSyncMeta(SyncFlags.PENDING_UPSERT, 50L),
                remoteUpdatedAt = 10L,
                remoteDeviceId = "device-a",
                localDeviceId = "device-a",
            ),
        )
    }

    @Test
    fun `dirty local applies newer same-device echo`() {
        assertEquals(
            FirestoreSyncRemoteApplyPolicy.Decision.Apply,
            FirestoreSyncRemoteApplyPolicy.decide(
                localMeta = LocalSyncMeta(SyncFlags.PENDING_UPSERT, 10L),
                remoteUpdatedAt = 50L,
                remoteDeviceId = "device-a",
                localDeviceId = "device-a",
            ),
        )
    }

    @Test
    fun `clean local uses LWW`() {
        assertEquals(
            FirestoreSyncRemoteApplyPolicy.Decision.Apply,
            FirestoreSyncRemoteApplyPolicy.decide(
                localMeta = LocalSyncMeta(SyncFlags.CLEAN, 10L),
                remoteUpdatedAt = 20L,
                remoteDeviceId = "b",
                localDeviceId = "a",
            ),
        )
        assertEquals(
            FirestoreSyncRemoteApplyPolicy.Decision.Skip,
            FirestoreSyncRemoteApplyPolicy.decide(
                localMeta = LocalSyncMeta(SyncFlags.CLEAN, 30L),
                remoteUpdatedAt = 20L,
                remoteDeviceId = "b",
                localDeviceId = "a",
            ),
        )
    }

    @Test
    fun `tombstone applies when clean and remote newer`() {
        assertEquals(
            FirestoreSyncRemoteApplyPolicy.Decision.Apply,
            FirestoreSyncRemoteApplyPolicy.decide(
                localMeta = LocalSyncMeta(SyncFlags.CLEAN, 10L),
                remoteUpdatedAt = 20L,
                remoteDeviceId = "b",
                localDeviceId = "a",
                remoteDeleted = true,
            ),
        )
    }

    @Test
    fun `older same-device tombstone does not beat dirty local`() {
        assertEquals(
            FirestoreSyncRemoteApplyPolicy.Decision.Skip,
            FirestoreSyncRemoteApplyPolicy.decide(
                localMeta = LocalSyncMeta(SyncFlags.PENDING_UPSERT, 100L),
                remoteUpdatedAt = 10L,
                remoteDeviceId = "device-a",
                localDeviceId = "device-a",
                remoteDeleted = true,
            ),
        )
    }

    @Test
    fun `newer same-device tombstone wins over dirty local`() {
        assertEquals(
            FirestoreSyncRemoteApplyPolicy.Decision.Apply,
            FirestoreSyncRemoteApplyPolicy.decide(
                localMeta = LocalSyncMeta(SyncFlags.PENDING_UPSERT, 10L),
                remoteUpdatedAt = 100L,
                remoteDeviceId = "device-a",
                localDeviceId = "device-a",
                remoteDeleted = true,
            ),
        )
    }

    @Test
    fun `localWinsLww is inverse of remoteWinsLww`() {
        assertTrue(
            FirestoreSyncRemoteApplyPolicy.localWinsLww(
                localUpdatedAt = 50L,
                remoteUpdatedAt = 10L,
                localDeviceId = "a",
                remoteDeviceId = "b",
            ),
        )
        assertTrue(
            !FirestoreSyncRemoteApplyPolicy.localWinsLww(
                localUpdatedAt = 10L,
                remoteUpdatedAt = 50L,
                localDeviceId = "a",
                remoteDeviceId = "b",
            ),
        )
    }
}

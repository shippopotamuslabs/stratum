package com.stratum.core.infrastructure.sync.handlers

import com.stratum.core.infrastructure.sync.LocalSyncMeta
import com.stratum.core.infrastructure.sync.SyncCollection
import com.stratum.core.infrastructure.sync.SyncFlags
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * LWW-loss adopt failure must force-clean local dirty (F10) so upload does not infinite-loop.
 */
class ForceCleanLocalTest {
    @Test
    @DisplayName("forceCleanLocal marks dirty row clean when clock unchanged")
    fun forceCleanDropsDirty() = runTest {
        var meta: LocalSyncMeta? = LocalSyncMeta(SyncFlags.PENDING_UPSERT, 42L)
        var markedAt: Long? = null
        val handler =
            GenericSyncHandler(
                collection = SyncCollection.UserProfile,
                loadPending = { emptyList() },
                loadLocalMeta = { meta },
                deleteById = {},
                markSynced = { _, at ->
                    markedAt = at
                    meta = LocalSyncMeta(SyncFlags.CLEAN, at)
                },
                upsertRemote = { _, _ -> false },
            )

        handler.forceCleanLocal("profile")

        assertEquals(42L, markedAt)
        assertEquals(SyncFlags.CLEAN, meta!!.pendingSync)
    }

    @Test
    @DisplayName("forceCleanLocal is no-op when row absent")
    fun forceCleanAbsentNoOp() = runTest {
        var markCalls = 0
        val handler =
            GenericSyncHandler(
                collection = SyncCollection.UserProfile,
                loadPending = { emptyList() },
                loadLocalMeta = { null },
                deleteById = {},
                markSynced = { _, _ -> markCalls++ },
                upsertRemote = { _, _ -> false },
            )

        handler.forceCleanLocal("missing")
        assertEquals(0, markCalls)
        assertNull(handler.localMeta("missing"))
    }

    @Test
    @DisplayName("cheap hasPending uses probe without encode")
    fun cheapHasPendingProbe() = runTest {
        var encodeCalls = 0
        val handler =
            GenericSyncHandler(
                collection = SyncCollection.Programs,
                loadPending = {
                    encodeCalls++
                    listOf(PendingSyncRow("p1", 1L, mapOf("name" to "x")))
                },
                loadLocalMeta = { null },
                deleteById = {},
                markSynced = { _, _ -> },
                upsertRemote = { _, _ -> true },
                checkHasPending = { true },
            )
        assertEquals(true, handler.hasPending())
        assertEquals(0, encodeCalls)
    }
}

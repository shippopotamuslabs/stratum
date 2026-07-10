package com.stratum.core.data.sync

/**
 * Unified remote-apply decision for every collection (dirty preserve + LWW).
 *
 * Rules:
 * - Absent local → apply.
 * - Dirty local + remote from another device → skip upserts (upload later), but
 *   **tombstones always go through LWW** so stale deletes cannot wipe newer local rows
 *   and newer remote deletes still win.
 * - Dirty local + same-device echo → LWW (covers same-device tombstone echoes too).
 * - Clean local → LWW (upserts and tombstones).
 */
object FirestoreSyncRemoteApplyPolicy {
    enum class Decision {
        /** Apply remote upsert or tombstone. */
        Apply,

        /** Keep local row unchanged. */
        Skip,
    }

    /**
     * @param localMeta null when the row is absent locally (always apply upserts / deletes).
     */
    fun decide(
        localMeta: LocalSyncMeta?,
        remoteUpdatedAt: Long,
        remoteDeviceId: String?,
        localDeviceId: String,
        remoteDeleted: Boolean = false,
    ): Decision {
        if (localMeta == null) return Decision.Apply

        val pending = localMeta.pendingSync
        if (pending != SyncFlags.CLEAN) {
            // Tombstones always use LWW (any device) — never short-circuit on remoteDeleted alone.
            if (remoteDeleted) {
                return if (remoteWinsLww(
                        localUpdatedAt = localMeta.updatedAtEpochMillis,
                        remoteUpdatedAt = remoteUpdatedAt,
                        localDeviceId = localDeviceId,
                        remoteDeviceId = remoteDeviceId,
                    )
                ) {
                    Decision.Apply
                } else {
                    Decision.Skip
                }
            }
            // Dirty local from another device (or missing remote device id) — upload wins later.
            if (remoteDeviceId == null || remoteDeviceId != localDeviceId) {
                return Decision.Skip
            }
            // Same-device upsert echo: apply only when remote wins LWW.
            return if (remoteWinsLww(
                    localUpdatedAt = localMeta.updatedAtEpochMillis,
                    remoteUpdatedAt = remoteUpdatedAt,
                    localDeviceId = localDeviceId,
                    remoteDeviceId = remoteDeviceId,
                )
            ) {
                Decision.Apply
            } else {
                Decision.Skip
            }
        }

        // Clean local — last-write-wins (tombstones included).
        return if (remoteWinsLww(
                localUpdatedAt = localMeta.updatedAtEpochMillis,
                remoteUpdatedAt = remoteUpdatedAt,
                localDeviceId = localDeviceId,
                remoteDeviceId = remoteDeviceId,
            )
        ) {
            Decision.Apply
        } else {
            Decision.Skip
        }
    }

    fun remoteWinsLww(
        localUpdatedAt: Long,
        remoteUpdatedAt: Long,
        localDeviceId: String?,
        remoteDeviceId: String?,
    ): Boolean {
        if (remoteUpdatedAt > localUpdatedAt) return true
        if (remoteUpdatedAt < localUpdatedAt) return false
        if (remoteDeviceId == null) return false
        if (localDeviceId == null) return true
        return remoteDeviceId > localDeviceId
    }

    /** Inverse of [remoteWinsLww] for conditional uploads. */
    fun localWinsLww(
        localUpdatedAt: Long,
        remoteUpdatedAt: Long,
        localDeviceId: String?,
        remoteDeviceId: String?,
    ): Boolean =
        !remoteWinsLww(
            localUpdatedAt = localUpdatedAt,
            remoteUpdatedAt = remoteUpdatedAt,
            localDeviceId = localDeviceId,
            remoteDeviceId = remoteDeviceId,
        )
}

/** Local dirty clock for conflict decisions. */
data class LocalSyncMeta(
    val pendingSync: Int,
    val updatedAtEpochMillis: Long,
)

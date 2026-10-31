package com.stratum.core.infrastructure.sync

/**
 * Local dirty flags for Room rows that participate in Firestore sync.
 *
 * Only stored in Room — never required on the wire. Deletes use the pending-cloud-delete
 * outbox ([CloudDeleteQueue]), not a row-level delete flag.
 *
 * This is **not** a product feature kill-switch — see [CloudSyncFeatureGate].
 */
object SyncFlags {
    /** Row matches last known cloud state (or is seed/catalog not yet user-touched). */
    const val CLEAN: Int = 0

    /** Local upsert waiting to upload. */
    const val PENDING_UPSERT: Int = 1
}

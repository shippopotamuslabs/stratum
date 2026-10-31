package com.stratum.core.infrastructure.sync

/** Wire-field names for client-authored sync metadata on Firestore documents. */
object FirestoreSyncMetadata {
    const val DEVICE_ID = "_sync_device_id"
    const val CLIENT_MUTATION_ID = "_sync_client_mutation_id"
    const val WRITTEN_AT = "_sync_written_at"
    const val UPDATED_AT = "updated_at_epoch_millis"
    const val DELETED = "deleted"

    fun envelope(
        deviceId: String,
        clientMutationId: String,
        writtenAt: Long,
        updatedAt: Long,
        deleted: Boolean = false,
    ): Map<String, Any?> =
        mapOf(
            DEVICE_ID to deviceId,
            CLIENT_MUTATION_ID to clientMutationId,
            WRITTEN_AT to writtenAt,
            UPDATED_AT to updatedAt,
            DELETED to deleted,
        )

    fun isDeleted(data: Map<String, Any?>): Boolean = data[DELETED] as? Boolean == true
}

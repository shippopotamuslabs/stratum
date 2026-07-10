package com.stratum.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * Outbox of deleted syncable entity ids. Document path uses [collectionPath]/[entityId].
 *
 * Primary key is deterministic `{collectionPath}/{entityId}` so re-enqueue de-dupes and
 * cancel-on-upsert can target a single row.
 */
@Entity(
    tableName = "pending_cloud_deletes",
    indices = [Index(value = ["collection_path", "entity_id"], unique = true)],
)
data class PendingCloudDeleteEntity(
    @PrimaryKey
    @ColumnInfo(name = "row_id")
    val rowId: String,
    @ColumnInfo(name = "collection_path")
    val collectionPath: String,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "enqueued_at_epoch_millis")
    val enqueuedAtEpochMillis: Long,
) {
    companion object {
        fun rowId(collectionPath: String, entityId: String): String = "$collectionPath/$entityId"
    }
}

@Dao
interface PendingCloudDeleteDao {
    @Upsert
    suspend fun upsertAll(rows: List<PendingCloudDeleteEntity>)

    @Query("SELECT * FROM pending_cloud_deletes ORDER BY enqueued_at_epoch_millis ASC LIMIT :limit")
    suspend fun peekPending(limit: Int = 500): List<PendingCloudDeleteEntity>

    @Query("DELETE FROM pending_cloud_deletes WHERE row_id IN (:rowIds)")
    suspend fun deleteByIds(rowIds: List<String>)

    @Query(
        """
        DELETE FROM pending_cloud_deletes
        WHERE collection_path = :collectionPath AND entity_id = :entityId
        """,
    )
    suspend fun cancel(collectionPath: String, entityId: String)

    @Query(
        """
        DELETE FROM pending_cloud_deletes
        WHERE collection_path = :collectionPath AND entity_id IN (:entityIds)
        """,
    )
    suspend fun cancelAll(collectionPath: String, entityIds: List<String>)

    @Query("DELETE FROM pending_cloud_deletes")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM pending_cloud_deletes")
    suspend fun count(): Int
}

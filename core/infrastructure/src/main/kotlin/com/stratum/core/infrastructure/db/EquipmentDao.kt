package com.stratum.core.infrastructure.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stratum.core.infrastructure.sync.SyncFlags
import com.stratum.domain.lift.identity.EquipmentId
import kotlinx.coroutines.flow.Flow

@Dao
interface EquipmentDao {
    /**
     * All rows INCLUDING tombstones — the lift join in `RoomLiftRepository` relies on
     * unfiltered reads so lifts keep resolving deleted equipment. Catalog surfaces
     * filter via `RoomEquipmentRepository.observeEquipment`, never here.
     */
    @Query("SELECT * FROM equipment ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<EquipmentEntity>>

    /** All rows including tombstones (see [observeAll]). */
    @Query("SELECT * FROM equipment ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<EquipmentEntity>

    @Query("SELECT * FROM equipment WHERE id = :id LIMIT 1")
    suspend fun getById(id: EquipmentId): EquipmentEntity?

    @Query(
        """
        SELECT * FROM equipment
        WHERE name = :name COLLATE NOCASE
        LIMIT 1
        """,
    )
    suspend fun findByNameIgnoreCase(name: String): EquipmentEntity?

    @Upsert
    suspend fun upsert(equipment: EquipmentEntity)

    @Upsert
    suspend fun upsertAll(rows: List<EquipmentEntity>)

    @Query(
        """
        UPDATE equipment
        SET name = :name,
            pending_sync = ${SyncFlags.PENDING_UPSERT},
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE id = :id AND isDeleted = 0
        """,
    )
    suspend fun rename(id: EquipmentId, name: String, updatedAtEpochMillis: Long)

    /**
     * Sets the deleted flag (tombstone) on equipment and marks it dirty for
     * cloud upload. The row, name, and tiers are retained. Eligibility (hidden
     * system rows refused) is enforced by the repository; this update only
     * refuses already-tombstoned rows. Returns 1 when a row was tombstoned.
     */
    @Query(
        """
        UPDATE equipment
        SET isDeleted = 1,
            pending_sync = ${SyncFlags.PENDING_UPSERT},
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE id = :id AND isDeleted = 0
        """,
    )
    suspend fun tombstoneById(id: EquipmentId, updatedAtEpochMillis: Long): Int

    /**
     * Replaces the adder stops (JSON array of storage-lb doubles, null = no adder).
     * Marks the row dirty for cloud upload like [rename].
     */
    @Query(
        """
        UPDATE equipment
        SET micro_options_json = :microOptionsJson,
            pending_sync = ${SyncFlags.PENDING_UPSERT},
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE id = :id
        """,
    )
    suspend fun updateMicroOptions(
        id: EquipmentId,
        microOptionsJson: String?,
        updatedAtEpochMillis: Long,
    )

    @Query("SELECT COUNT(*) FROM equipment WHERE isCustom = 0")
    suspend fun countSeedEquipment(): Int

    @Query("SELECT * FROM equipment WHERE pending_sync != ${SyncFlags.CLEAN} LIMIT :limit")
    suspend fun getPending(limit: Int = 500): List<EquipmentEntity>

    @Query(
        """
        UPDATE equipment
        SET pending_sync = ${SyncFlags.CLEAN}
        WHERE id = :id AND updated_at_epoch_millis = :updatedAt
        """,
    )
    suspend fun markSyncedIfUnchanged(id: String, updatedAt: Long)

    @Query("DELETE FROM equipment WHERE id = :id")
    suspend fun deleteById(id: EquipmentId)
}

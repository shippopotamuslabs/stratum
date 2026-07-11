package com.stratum.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.lift.identity.EquipmentId
import kotlinx.coroutines.flow.Flow

@Dao
interface EquipmentDao {
    @Query("SELECT * FROM equipment ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<EquipmentEntity>>

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
        WHERE id = :id
        """,
    )
    suspend fun rename(id: EquipmentId, name: String, updatedAtEpochMillis: Long)

    @Query("DELETE FROM equipment WHERE id = :id AND isCustom = 1")
    suspend fun deleteCustomById(id: EquipmentId): Int

    @Query("SELECT COUNT(*) FROM lifts WHERE equipmentId = :equipmentId")
    suspend fun liftReferenceCount(equipmentId: EquipmentId): Int

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

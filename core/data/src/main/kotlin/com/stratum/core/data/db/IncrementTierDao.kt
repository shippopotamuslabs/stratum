package com.stratum.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.IncrementTierId
import com.stratum.domain.lift.identity.LiftId

/**
 * DAO for lift-override and equipment-default increment tiers. Writes replace the whole set
 * per parent (delete + insert in a transaction at the repository layer).
 */
@Dao
interface IncrementTierDao {
    @Upsert
    suspend fun upsertAll(tiers: List<IncrementTierEntity>)

    @Query("SELECT id FROM increment_tiers WHERE liftId = :liftId")
    suspend fun idsForLift(liftId: LiftId): List<String>

    @Query("DELETE FROM increment_tiers WHERE liftId = :liftId")
    suspend fun deleteForLift(liftId: LiftId)

    @Query("SELECT id FROM increment_tiers WHERE equipmentId = :equipmentId")
    suspend fun idsForEquipment(equipmentId: EquipmentId): List<String>

    @Query("DELETE FROM increment_tiers WHERE equipmentId = :equipmentId")
    suspend fun deleteForEquipment(equipmentId: EquipmentId)

    @Query("SELECT * FROM increment_tiers WHERE equipmentId = :equipmentId ORDER BY lowerBound")
    suspend fun getForEquipment(equipmentId: EquipmentId): List<IncrementTierEntity>

    @Query("SELECT * FROM increment_tiers WHERE equipmentId IS NOT NULL ORDER BY equipmentId, lowerBound")
    suspend fun getAllEquipmentTiers(): List<IncrementTierEntity>

    @Query("SELECT * FROM increment_tiers WHERE equipmentId IS NOT NULL ORDER BY equipmentId, lowerBound")
    fun observeAllEquipmentTiers(): kotlinx.coroutines.flow.Flow<List<IncrementTierEntity>>

    @Query(
        """
        SELECT * FROM increment_tiers
        WHERE pending_sync != ${SyncFlags.CLEAN}
        LIMIT :limit
        """,
    )
    suspend fun getPending(limit: Int = 500): List<IncrementTierEntity>

    @Query(
        """
        UPDATE increment_tiers
        SET pending_sync = ${SyncFlags.CLEAN}
        WHERE id = :id AND updated_at_epoch_millis = :updatedAt
        """,
    )
    suspend fun markSyncedIfUnchanged(id: String, updatedAt: Long)

    @Query("SELECT * FROM increment_tiers WHERE id = :id LIMIT 1")
    suspend fun getById(id: IncrementTierId): IncrementTierEntity?

    @Query("DELETE FROM increment_tiers WHERE id = :id")
    suspend fun deleteById(id: IncrementTierId)
}

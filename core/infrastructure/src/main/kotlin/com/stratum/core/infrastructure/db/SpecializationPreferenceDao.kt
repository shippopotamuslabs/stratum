package com.stratum.core.infrastructure.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stratum.core.infrastructure.sync.SyncFlags
import com.stratum.domain.onboarding.identity.SpecializationPreferenceId
import kotlinx.coroutines.flow.Flow

@Dao
interface SpecializationPreferenceDao {
    @Query("SELECT * FROM specialization_preference")
    fun observeAll(): Flow<List<SpecializationPreferenceEntity>>

    @Query("SELECT * FROM specialization_preference")
    suspend fun getAll(): List<SpecializationPreferenceEntity>

    @Query("SELECT * FROM specialization_preference WHERE id = :id LIMIT 1")
    suspend fun getById(id: SpecializationPreferenceId): SpecializationPreferenceEntity?

    @Query("SELECT id FROM specialization_preference")
    suspend fun allIds(): List<String>

    @Query("DELETE FROM specialization_preference")
    suspend fun clear()

    @Query("DELETE FROM specialization_preference WHERE id = :id")
    suspend fun deleteById(id: SpecializationPreferenceId)

    @Upsert
    suspend fun upsertAll(entities: List<SpecializationPreferenceEntity>)

    @Query(
        """
        SELECT * FROM specialization_preference
        WHERE pending_sync != ${SyncFlags.CLEAN}
        LIMIT :limit
        """,
    )
    suspend fun getPending(limit: Int = 100): List<SpecializationPreferenceEntity>

    @Query(
        """
        UPDATE specialization_preference
        SET pending_sync = ${SyncFlags.CLEAN}
        WHERE id = :id AND updated_at_epoch_millis = :updatedAt
        """,
    )
    suspend fun markSyncedIfUnchanged(id: String, updatedAt: Long)
}

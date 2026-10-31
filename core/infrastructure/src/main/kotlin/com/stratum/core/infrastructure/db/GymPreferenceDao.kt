package com.stratum.core.infrastructure.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stratum.core.infrastructure.sync.SyncFlags
import com.stratum.domain.onboarding.identity.GymPreferenceId
import kotlinx.coroutines.flow.Flow

@Dao
interface GymPreferenceDao {
    @Query("SELECT * FROM gym_preference WHERE id = :id LIMIT 1")
    fun observe(id: GymPreferenceId = GymPreferenceId.Default): Flow<GymPreferenceEntity?>

    @Query("SELECT * FROM gym_preference WHERE id = :id LIMIT 1")
    suspend fun getOrNull(id: GymPreferenceId = GymPreferenceId.Default): GymPreferenceEntity?

    @Upsert
    suspend fun upsert(entity: GymPreferenceEntity)

    @Query(
        """
        SELECT * FROM gym_preference
        WHERE pending_sync != ${SyncFlags.CLEAN}
        LIMIT :limit
        """,
    )
    suspend fun getPending(limit: Int = 50): List<GymPreferenceEntity>

    @Query(
        """
        UPDATE gym_preference
        SET pending_sync = ${SyncFlags.CLEAN}
        WHERE id = :id AND updated_at_epoch_millis = :updatedAt
        """,
    )
    suspend fun markSyncedIfUnchanged(id: String, updatedAt: Long)

    @Query("DELETE FROM gym_preference WHERE id = :id")
    suspend fun deleteById(id: com.stratum.domain.onboarding.identity.GymPreferenceId)
}

package com.stratum.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.onboarding.identity.UserProfileId
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_training_profile WHERE id = :id LIMIT 1")
    fun observe(id: UserProfileId = UserProfileId.Default): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_training_profile WHERE id = :id LIMIT 1")
    suspend fun getOrNull(id: UserProfileId = UserProfileId.Default): UserProfileEntity?

    @Upsert
    suspend fun upsert(entity: UserProfileEntity)

    @Query(
        """
        SELECT * FROM user_training_profile
        WHERE pending_sync != ${SyncFlags.CLEAN}
        LIMIT :limit
        """,
    )
    suspend fun getPending(limit: Int = 50): List<UserProfileEntity>

    @Query(
        """
        UPDATE user_training_profile
        SET pending_sync = ${SyncFlags.CLEAN}
        WHERE id = :id AND updated_at_epoch_millis = :updatedAt
        """,
    )
    suspend fun markSyncedIfUnchanged(id: String, updatedAt: Long)

    @Query("DELETE FROM user_training_profile WHERE id = :id")
    suspend fun deleteById(id: UserProfileId)
}

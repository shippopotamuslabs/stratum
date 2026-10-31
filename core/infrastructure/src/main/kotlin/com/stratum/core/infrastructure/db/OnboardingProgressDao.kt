package com.stratum.core.infrastructure.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stratum.core.infrastructure.sync.SyncFlags
import com.stratum.domain.onboarding.identity.OnboardingProgressId
import kotlinx.coroutines.flow.Flow

@Dao
interface OnboardingProgressDao {
    @Query("SELECT * FROM onboarding_progress WHERE id = :id LIMIT 1")
    fun observe(id: OnboardingProgressId = OnboardingProgressId.Default): Flow<OnboardingProgressEntity?>

    @Query("SELECT * FROM onboarding_progress WHERE id = :id LIMIT 1")
    suspend fun getOrNull(id: OnboardingProgressId = OnboardingProgressId.Default): OnboardingProgressEntity?

    @Upsert
    suspend fun upsert(entity: OnboardingProgressEntity)

    @Query(
        """
        SELECT * FROM onboarding_progress
        WHERE pending_sync != ${SyncFlags.CLEAN}
        LIMIT :limit
        """,
    )
    suspend fun getPending(limit: Int = 50): List<OnboardingProgressEntity>

    @Query(
        """
        UPDATE onboarding_progress
        SET pending_sync = ${SyncFlags.CLEAN}
        WHERE id = :id AND updated_at_epoch_millis = :updatedAt
        """,
    )
    suspend fun markSyncedIfUnchanged(id: String, updatedAt: Long)

    @Query("DELETE FROM onboarding_progress WHERE id = :id")
    suspend fun deleteById(id: com.stratum.domain.onboarding.identity.OnboardingProgressId)
}

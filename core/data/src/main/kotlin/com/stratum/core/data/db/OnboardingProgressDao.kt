package com.stratum.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface OnboardingProgressDao {
    @Query("SELECT * FROM onboarding_progress WHERE id = ${OnboardingProgressEntity.ID}")
    fun observe(): Flow<OnboardingProgressEntity?>

    @Query("SELECT * FROM onboarding_progress WHERE id = ${OnboardingProgressEntity.ID}")
    suspend fun getOrNull(): OnboardingProgressEntity?

    @Upsert
    suspend fun upsert(entity: OnboardingProgressEntity)
}

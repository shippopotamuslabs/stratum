package com.stratum.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Single-row DAO for the gym-preset choice, mirroring [OnboardingProgressDao]. [selectedPreset]
 * is nullable so the row can represent "skipped the preset, curated manually" (`null`).
 */
@Dao
interface GymPreferenceDao {
    @Query("SELECT * FROM gym_preference WHERE id = ${GymPreferenceEntity.ID}")
    fun observe(): Flow<GymPreferenceEntity?>

    @Query("SELECT * FROM gym_preference WHERE id = ${GymPreferenceEntity.ID}")
    suspend fun getOrNull(): GymPreferenceEntity?

    @Upsert
    suspend fun upsert(entity: GymPreferenceEntity)
}

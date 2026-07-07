package com.stratum.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SpecializationPreferenceDao {
    @Query("SELECT * FROM specialization_preference")
    fun observeAll(): Flow<List<SpecializationPreferenceEntity>>

    @Query("SELECT * FROM specialization_preference")
    suspend fun getAll(): List<SpecializationPreferenceEntity>

    @Query("DELETE FROM specialization_preference")
    suspend fun clear()

    @Upsert
    suspend fun upsertAll(entities: List<SpecializationPreferenceEntity>)
}

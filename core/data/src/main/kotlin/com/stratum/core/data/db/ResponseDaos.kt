package com.stratum.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ResponseEpochDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ResponseEpochEntity>)

    @Query("DELETE FROM response_epochs")
    suspend fun deleteAll()

    @Query("SELECT * FROM response_epochs")
    suspend fun getAll(): List<ResponseEpochEntity>
}

@Dao
interface VolumeResponseObservationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<VolumeResponseObservationEntity>)

    @Query("DELETE FROM volume_response_observations")
    suspend fun deleteAll()

    @Query("SELECT * FROM volume_response_observations")
    suspend fun getAll(): List<VolumeResponseObservationEntity>
}

@Dao
interface LiftEffectivenessObservationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<LiftEffectivenessObservationEntity>)

    @Query("DELETE FROM lift_effectiveness_observations")
    suspend fun deleteAll()

    @Query("SELECT * FROM lift_effectiveness_observations")
    suspend fun getAll(): List<LiftEffectivenessObservationEntity>
}

@Dao
interface ExperimentBlockDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ExperimentBlockEntity>)

    @Query("DELETE FROM experiment_blocks")
    suspend fun deleteAll()

    @Query("SELECT * FROM experiment_blocks")
    suspend fun getAll(): List<ExperimentBlockEntity>
}

@Dao
interface ResponseProfileMetadataDao {
    @Upsert
    suspend fun upsert(entity: ResponseProfileMetadataEntity)

    @Query("SELECT * FROM response_profile_metadata WHERE id = 0 LIMIT 1")
    suspend fun get(): ResponseProfileMetadataEntity?

    @Query("DELETE FROM response_profile_metadata")
    suspend fun deleteAll()
}

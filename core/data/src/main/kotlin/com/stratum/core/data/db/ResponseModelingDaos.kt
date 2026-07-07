package com.stratum.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface MuscleWeeklyStimulusDao {
    @Upsert
    suspend fun upsertAll(entities: List<MuscleWeeklyStimulusEntity>)

    @Query("DELETE FROM muscle_weekly_stimulus")
    suspend fun deleteAll()

    @Query("SELECT * FROM muscle_weekly_stimulus")
    suspend fun getAll(): List<MuscleWeeklyStimulusEntity>
}

@Dao
interface LiftWeeklyPerformanceDao {
    @Upsert
    suspend fun upsertAll(entities: List<LiftWeeklyPerformanceEntity>)

    @Query("DELETE FROM lift_weekly_performance")
    suspend fun deleteAll()

    @Query("SELECT * FROM lift_weekly_performance")
    suspend fun getAll(): List<LiftWeeklyPerformanceEntity>
}

@Dao
interface MuscleWeeklyOutcomeIndexDao {
    @Upsert
    suspend fun upsertAll(entities: List<MuscleWeeklyOutcomeIndexEntity>)

    @Query("DELETE FROM muscle_weekly_outcome_indices")
    suspend fun deleteAll()

    @Query("SELECT * FROM muscle_weekly_outcome_indices")
    suspend fun getAll(): List<MuscleWeeklyOutcomeIndexEntity>
}

@Dao
interface MuscleDoseResponsePointDao {
    @Upsert
    suspend fun upsertAll(entities: List<MuscleDoseResponsePointEntity>)

    @Query("DELETE FROM muscle_dose_response_points")
    suspend fun deleteAll()

    @Query("SELECT * FROM muscle_dose_response_points")
    suspend fun getAll(): List<MuscleDoseResponsePointEntity>
}

@Dao
interface MuscleVolumeResponseSnapshotDao {
    @Upsert
    suspend fun upsertAll(entities: List<MuscleVolumeResponseSnapshotEntity>)

    @Query("DELETE FROM muscle_volume_response_snapshots")
    suspend fun deleteAll()

    @Query("SELECT * FROM muscle_volume_response_snapshots")
    suspend fun getAll(): List<MuscleVolumeResponseSnapshotEntity>
}

@Dao
interface MuscleResponseModelDiagnosticDao {
    @Upsert
    suspend fun upsertAll(entities: List<MuscleResponseModelDiagnosticEntity>)

    @Query("DELETE FROM muscle_response_model_diagnostics")
    suspend fun deleteAll()

    @Query("SELECT * FROM muscle_response_model_diagnostics")
    suspend fun getAll(): List<MuscleResponseModelDiagnosticEntity>
}

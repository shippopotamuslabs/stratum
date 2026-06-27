package com.stratum.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.stratum.domain.lift.identity.LiftId
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportJobDao {
    @Upsert
    suspend fun upsert(entity: ImportJobEntity)

    @Query("SELECT * FROM import_jobs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ImportJobEntity?

    @Query(
        """
        SELECT * FROM import_jobs
        WHERE status NOT IN ('Completed', 'Failed')
        ORDER BY updatedAtEpochMillis DESC
        LIMIT 1
        """,
    )
    suspend fun getActive(): ImportJobEntity?
}

@Dao
interface HistoricalSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<HistoricalSessionEntity>)

    @Query("DELETE FROM historical_sessions")
    suspend fun deleteAll()

    @Query("SELECT * FROM historical_sessions")
    suspend fun getAll(): List<HistoricalSessionEntity>
}

@Dao
interface LoggedSetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<LoggedSetEntity>)

    @Query("DELETE FROM logged_sets")
    suspend fun deleteAll()

    @Query("SELECT * FROM logged_sets")
    suspend fun getAll(): List<LoggedSetEntity>

    @Query(
        """
        SELECT * FROM logged_sets
        WHERE completedAtEpochMillis >= :startMillis AND completedAtEpochMillis <= :endMillis
        """,
    )
    suspend fun getInWindow(startMillis: Long, endMillis: Long): List<LoggedSetEntity>
}

@Dao
interface LiftTrainingInsightsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<LiftTrainingInsightsEntity>)

    @Query("DELETE FROM lift_training_insights")
    suspend fun deleteAll()

    @Query("SELECT * FROM lift_training_insights")
    suspend fun getAll(): List<LiftTrainingInsightsEntity>

    @Query("SELECT * FROM lift_training_insights WHERE liftId = :liftId LIMIT 1")
    suspend fun getByLiftId(liftId: LiftId): LiftTrainingInsightsEntity?
}

@Dao
interface SupersetAffinityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SupersetAffinityEntity>)

    @Query("DELETE FROM superset_affinities")
    suspend fun deleteAll()

    @Query("SELECT * FROM superset_affinities")
    suspend fun getAll(): List<SupersetAffinityEntity>
}

@Dao
interface MuscleVolumeBaselineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<MuscleVolumeBaselineEntity>)

    @Query("DELETE FROM muscle_volume_baselines")
    suspend fun deleteAll()

    @Query("SELECT * FROM muscle_volume_baselines")
    suspend fun getAll(): List<MuscleVolumeBaselineEntity>
}

@Dao
interface ImportAnalysisMetadataDao {
    @Upsert
    suspend fun upsert(entity: ImportAnalysisMetadataEntity)

    @Query("SELECT * FROM import_analysis_metadata WHERE id = 0 LIMIT 1")
    suspend fun get(): ImportAnalysisMetadataEntity?

    @Query("DELETE FROM import_analysis_metadata")
    suspend fun deleteAll()
}

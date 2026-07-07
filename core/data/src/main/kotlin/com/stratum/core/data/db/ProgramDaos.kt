package com.stratum.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgramDao {
    @Upsert
    suspend fun upsert(entity: ProgramEntity)

    @Query("DELETE FROM programs")
    suspend fun deleteAll()

    @Query("DELETE FROM programs WHERE id = :programId")
    suspend fun deleteById(programId: com.stratum.domain.program.identity.ProgramId)

    @Query("SELECT * FROM programs LIMIT 1")
    suspend fun get(): ProgramEntity?

    @Query("SELECT * FROM programs LIMIT 1")
    fun observe(): Flow<ProgramEntity?>

    @Query(
        """
        UPDATE programs
        SET current_cycle_number = :currentCycleNumber,
            cycle_position = :cyclePosition
        WHERE id = :programId
        """,
    )
    suspend fun updateCyclePointer(
        programId: com.stratum.domain.program.identity.ProgramId,
        currentCycleNumber: Int,
        cyclePosition: Int,
    )
}

@Dao
interface WorkoutDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<WorkoutEntity>)

    @Query("DELETE FROM workouts")
    suspend fun deleteAll()

    @Query("SELECT * FROM workouts WHERE program_id = :programId ORDER BY position")
    suspend fun getForProgram(programId: String): List<WorkoutEntity>

    @Query("SELECT * FROM workouts ORDER BY position")
    fun observeAll(): Flow<List<WorkoutEntity>>
}

@Dao
interface WorkoutLiftDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<WorkoutLiftEntity>)

    @Query("DELETE FROM workout_lifts")
    suspend fun deleteAll()

    @Query("SELECT * FROM workout_lifts WHERE workout_id = :workoutId ORDER BY position")
    suspend fun getForWorkout(workoutId: String): List<WorkoutLiftEntity>

    @Query("SELECT * FROM workout_lifts ORDER BY position")
    fun observeAll(): Flow<List<WorkoutLiftEntity>>
}

@Dao
interface LiftSetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<LiftSetEntity>)

    @Query("DELETE FROM sets")
    suspend fun deleteAll()

    @Query("SELECT * FROM sets WHERE workout_lift_id = :workoutLiftId ORDER BY position")
    suspend fun getForWorkoutLift(workoutLiftId: String): List<LiftSetEntity>

    @Query("SELECT * FROM sets ORDER BY position")
    fun observeAll(): Flow<List<LiftSetEntity>>
}

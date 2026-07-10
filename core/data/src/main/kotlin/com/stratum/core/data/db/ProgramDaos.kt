package com.stratum.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stratum.domain.program.identity.ProgramId
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgramDao {
    @Upsert
    suspend fun upsert(entity: ProgramEntity)

    @Query("DELETE FROM programs WHERE id = :programId")
    suspend fun deleteById(programId: ProgramId)

    @Query(
        """
        SELECT * FROM programs
        WHERE is_active = 1 AND id != 'import-history'
        LIMIT 1
        """,
    )
    suspend fun getActive(): ProgramEntity?

    @Query(
        """
        SELECT * FROM programs
        WHERE is_active = 1 AND id != 'import-history'
        LIMIT 1
        """,
    )
    fun observeActive(): Flow<ProgramEntity?>

    @Query("SELECT * FROM programs WHERE id = :programId LIMIT 1")
    suspend fun getById(programId: ProgramId): ProgramEntity?

    @Query("SELECT * FROM programs WHERE id = :programId LIMIT 1")
    fun observeById(programId: ProgramId): Flow<ProgramEntity?>

    @Query(
        """
        SELECT * FROM programs
        WHERE is_active = 0 AND id != 'import-history'
        ORDER BY created_at DESC
        """,
    )
    fun observeLibrary(): Flow<List<ProgramEntity>>

    @Query(
        """
        UPDATE programs
        SET is_active = 0
        WHERE id != 'import-history'
        """,
    )
    suspend fun clearActiveFlags()

    @Query(
        """
        UPDATE programs
        SET is_active = :isActive
        WHERE id = :programId
        """,
    )
    suspend fun setActive(programId: ProgramId, isActive: Boolean)

    @Query(
        """
        UPDATE programs
        SET current_cycle_number = :currentCycleNumber,
            cycle_position = :cyclePosition
        WHERE id = :programId
        """,
    )
    suspend fun updateCyclePointer(
        programId: ProgramId,
        currentCycleNumber: Int,
        cyclePosition: Int,
    )
}

@Dao
interface WorkoutDao {
    @Upsert
    suspend fun upsertAll(entities: List<WorkoutEntity>)

    @Query("DELETE FROM workouts WHERE program_id = :programId")
    suspend fun deleteForProgram(programId: ProgramId)

    @Query("SELECT * FROM workouts WHERE program_id = :programId ORDER BY position")
    suspend fun getForProgram(programId: String): List<WorkoutEntity>

    @Query("SELECT * FROM workouts ORDER BY position")
    fun observeAll(): Flow<List<WorkoutEntity>>
}

@Dao
interface WorkoutLiftDao {
    @Upsert
    suspend fun upsertAll(entities: List<WorkoutLiftEntity>)

    @Query("SELECT * FROM workout_lifts WHERE workout_id = :workoutId ORDER BY position")
    suspend fun getForWorkout(workoutId: String): List<WorkoutLiftEntity>

    @Query("SELECT * FROM workout_lifts ORDER BY position")
    fun observeAll(): Flow<List<WorkoutLiftEntity>>
}

@Dao
interface LiftSetDao {
    @Upsert
    suspend fun upsertAll(entities: List<LiftSetEntity>)

    @Query("SELECT * FROM sets WHERE workout_lift_id = :workoutLiftId ORDER BY position")
    suspend fun getForWorkoutLift(workoutLiftId: String): List<LiftSetEntity>

    @Query("SELECT * FROM sets ORDER BY position")
    fun observeAll(): Flow<List<LiftSetEntity>>
}

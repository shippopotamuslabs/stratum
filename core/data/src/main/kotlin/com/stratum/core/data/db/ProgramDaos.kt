package com.stratum.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
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
        SET is_active = 0,
            pending_sync = ${SyncFlags.PENDING_UPSERT},
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE id != 'import-history' AND is_active = 1
        """,
    )
    suspend fun clearActiveFlags(updatedAtEpochMillis: Long)

    /**
     * Remote-apply path: demote other actives with a non-regressing clock + PENDING_UPSERT
     * so dual-active cloud heals on next upload (invariant side-effects of remote winners
     * mark dirty — same rule as PR demotion).
     */
    @Query(
        """
        UPDATE programs
        SET is_active = 0,
            pending_sync = ${SyncFlags.PENDING_UPSERT},
            updated_at_epoch_millis = CASE
                WHEN updated_at_epoch_millis < :minUpdatedAtEpochMillis
                THEN :minUpdatedAtEpochMillis
                ELSE updated_at_epoch_millis
            END
        WHERE id != 'import-history'
          AND is_active = 1
          AND id != :exceptProgramId
        """,
    )
    suspend fun demoteOtherActivesPending(
        exceptProgramId: ProgramId,
        minUpdatedAtEpochMillis: Long,
    )

    @Query(
        """
        UPDATE programs
        SET is_active = :isActive,
            pending_sync = ${SyncFlags.PENDING_UPSERT},
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE id = :programId
        """,
    )
    suspend fun setActive(
        programId: ProgramId,
        isActive: Boolean,
        updatedAtEpochMillis: Long,
    )

    @Query(
        """
        UPDATE programs
        SET current_cycle_number = :currentCycleNumber,
            cycle_position = :cyclePosition,
            cycle_start_epoch_day = CASE
                WHEN :cycleStartEpochDay IS NOT NULL THEN :cycleStartEpochDay
                ELSE cycle_start_epoch_day
            END,
            pending_sync = ${SyncFlags.PENDING_UPSERT},
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE id = :programId
        """,
    )
    suspend fun updateCyclePointer(
        programId: ProgramId,
        currentCycleNumber: Int,
        cyclePosition: Int,
        cycleStartEpochDay: Long?,
        updatedAtEpochMillis: Long,
    )

    /** Ids of non-import programs currently marked active (for outbox / diagnostics). */
    @Query("SELECT id FROM programs WHERE is_active = 1 AND id != 'import-history'")
    suspend fun activeProgramIds(): List<ProgramId>

    @Query("SELECT * FROM programs WHERE pending_sync != ${SyncFlags.CLEAN} LIMIT :limit")
    suspend fun getPending(limit: Int = 200): List<ProgramEntity>

    @Query(
        """
        UPDATE programs
        SET pending_sync = ${SyncFlags.CLEAN}
        WHERE id = :id AND updated_at_epoch_millis = :updatedAt
        """,
    )
    suspend fun markSyncedIfUnchanged(id: String, updatedAt: Long)

    @Query("SELECT * FROM programs")
    suspend fun getAll(): List<ProgramEntity>
}

@Dao
interface WorkoutDao {
    @Upsert
    suspend fun upsertAll(entities: List<WorkoutEntity>)

    @Query("SELECT * FROM workouts WHERE id = :id LIMIT 1")
    suspend fun getById(id: WorkoutId): WorkoutEntity?

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteById(id: WorkoutId)

    @Query("DELETE FROM workouts WHERE program_id = :programId")
    suspend fun deleteForProgram(programId: ProgramId)

    @Query("SELECT * FROM workouts WHERE program_id = :programId ORDER BY position")
    suspend fun getForProgram(programId: String): List<WorkoutEntity>

    @Query("SELECT id FROM workouts WHERE program_id = :programId")
    suspend fun idsForProgram(programId: ProgramId): List<String>

    @Query("SELECT * FROM workouts ORDER BY position")
    fun observeAll(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE pending_sync != ${SyncFlags.CLEAN} LIMIT :limit")
    suspend fun getPending(limit: Int = 500): List<WorkoutEntity>

    @Query(
        """
        UPDATE workouts
        SET pending_sync = ${SyncFlags.CLEAN}
        WHERE id = :id AND updated_at_epoch_millis = :updatedAt
        """,
    )
    suspend fun markSyncedIfUnchanged(id: String, updatedAt: Long)
}

@Dao
interface WorkoutLiftDao {
    @Upsert
    suspend fun upsertAll(entities: List<WorkoutLiftEntity>)

    @Query("SELECT * FROM workout_lifts WHERE id = :id LIMIT 1")
    suspend fun getById(id: WorkoutLiftId): WorkoutLiftEntity?

    @Query("DELETE FROM workout_lifts WHERE id = :id")
    suspend fun deleteById(id: WorkoutLiftId)

    @Query("SELECT * FROM workout_lifts WHERE workout_id = :workoutId ORDER BY position")
    suspend fun getForWorkout(workoutId: String): List<WorkoutLiftEntity>

    @Query(
        """
        SELECT wl.id FROM workout_lifts wl
        INNER JOIN workouts w ON w.id = wl.workout_id
        WHERE w.program_id = :programId
        """,
    )
    suspend fun idsForProgram(programId: ProgramId): List<String>

    @Query("SELECT * FROM workout_lifts ORDER BY position")
    fun observeAll(): Flow<List<WorkoutLiftEntity>>

    @Query("SELECT * FROM workout_lifts")
    suspend fun getAll(): List<WorkoutLiftEntity>

    @Query("SELECT * FROM workout_lifts WHERE pending_sync != ${SyncFlags.CLEAN} LIMIT :limit")
    suspend fun getPending(limit: Int = 500): List<WorkoutLiftEntity>

    @Query(
        """
        UPDATE workout_lifts
        SET pending_sync = ${SyncFlags.CLEAN}
        WHERE id = :id AND updated_at_epoch_millis = :updatedAt
        """,
    )
    suspend fun markSyncedIfUnchanged(id: String, updatedAt: Long)

    /**
     * Targeted pin-note write. Returns affected row count (0 when the slot is not in Room).
     */
    @Query(
        """
        UPDATE workout_lifts
        SET pinned_note = :note,
            pending_sync = ${SyncFlags.PENDING_UPSERT},
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE id = :id
        """,
    )
    suspend fun setPinnedNote(
        id: WorkoutLiftId,
        note: String?,
        updatedAtEpochMillis: Long,
    ): Int

    /**
     * Re-points template slots from source catalog ids onto [targetLiftId] without rewriting the
     * full program hierarchy (merge-lifts path).
     */
    @Query(
        """
        UPDATE workout_lifts
        SET lift_id = :targetLiftId,
            pending_sync = ${SyncFlags.PENDING_UPSERT},
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE lift_id IN (:sourceLiftIds)
        """,
    )
    suspend fun repointLiftIds(
        sourceLiftIds: List<LiftId>,
        targetLiftId: LiftId,
        updatedAtEpochMillis: Long,
    ): Int

    /** Slots explicitly assigned [equipmentId] (STR-105 delete sweep). */
    @Query("SELECT * FROM workout_lifts WHERE equipment_id = :equipmentId")
    suspend fun getByEquipmentId(equipmentId: com.stratum.domain.lift.identity.EquipmentId): List<WorkoutLiftEntity>

    /**
     * Re-points template slots off deleted equipment onto its replacement
     * (STR-105 Scope 4.3). Null (inherit) slots are never matched.
     */
    @Query(
        """
        UPDATE workout_lifts
        SET equipment_id = :replacementId,
            pending_sync = ${SyncFlags.PENDING_UPSERT},
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE equipment_id = :deletedId
        """,
    )
    suspend fun repointEquipmentId(
        deletedId: com.stratum.domain.lift.identity.EquipmentId,
        replacementId: com.stratum.domain.lift.identity.EquipmentId,
        updatedAtEpochMillis: Long,
    ): Int
}

@Dao
interface LiftSetDao {
    @Upsert
    suspend fun upsertAll(entities: List<LiftSetEntity>)

    @Query("SELECT * FROM sets WHERE id = :id LIMIT 1")
    suspend fun getById(id: LiftSetId): LiftSetEntity?

    @Query("DELETE FROM sets WHERE id = :id")
    suspend fun deleteById(id: LiftSetId)

    @Query("SELECT * FROM sets WHERE workout_lift_id = :workoutLiftId ORDER BY position")
    suspend fun getForWorkoutLift(workoutLiftId: String): List<LiftSetEntity>

    @Query("SELECT id FROM sets WHERE workout_lift_id = :workoutLiftId")
    suspend fun idsForWorkoutLift(workoutLiftId: String): List<String>

    @Query(
        """
        SELECT s.id FROM sets s
        INNER JOIN workout_lifts wl ON wl.id = s.workout_lift_id
        INNER JOIN workouts w ON w.id = wl.workout_id
        WHERE w.program_id = :programId
        """,
    )
    suspend fun idsForProgram(programId: ProgramId): List<String>

    @Query("SELECT * FROM sets ORDER BY position")
    fun observeAll(): Flow<List<LiftSetEntity>>

    @Query("SELECT * FROM sets WHERE pending_sync != ${SyncFlags.CLEAN} LIMIT :limit")
    suspend fun getPending(limit: Int = 500): List<LiftSetEntity>

    @Query(
        """
        UPDATE sets
        SET pending_sync = ${SyncFlags.CLEAN}
        WHERE id = :id AND updated_at_epoch_millis = :updatedAt
        """,
    )
    suspend fun markSyncedIfUnchanged(id: String, updatedAt: Long)
}

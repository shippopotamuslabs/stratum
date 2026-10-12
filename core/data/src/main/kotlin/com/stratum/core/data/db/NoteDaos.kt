package com.stratum.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.lift.identity.LiftCatalogNoteId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutLiftNoteId

/** Ordered note-list reads/writes for one catalog lift. */
@Dao
interface LiftNoteDao {
    @Upsert
    suspend fun upsertAll(rows: List<LiftNoteEntity>)

    @Query("SELECT * FROM lift_notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: LiftCatalogNoteId): LiftNoteEntity?

    @Query("SELECT * FROM lift_notes WHERE lift_id = :liftId ORDER BY position ASC")
    suspend fun getForLift(liftId: LiftId): List<LiftNoteEntity>

    @Query("SELECT id FROM lift_notes WHERE lift_id = :liftId ORDER BY position ASC")
    suspend fun idsForLift(liftId: LiftId): List<LiftCatalogNoteId>

    @Query("DELETE FROM lift_notes WHERE lift_id = :liftId")
    suspend fun deleteForLift(liftId: LiftId)

    @Query("DELETE FROM lift_notes WHERE id = :id")
    suspend fun deleteById(id: LiftCatalogNoteId)

    @Query("SELECT * FROM lift_notes WHERE pending_sync != ${SyncFlags.CLEAN} LIMIT :limit")
    suspend fun getPending(limit: Int = 500): List<LiftNoteEntity>

    @Query(
        """
        UPDATE lift_notes
        SET pending_sync = ${SyncFlags.CLEAN}
        WHERE id = :id AND updated_at_epoch_millis = :updatedAt
        """,
    )
    suspend fun markSyncedIfUnchanged(id: String, updatedAt: Long)
}

/** Ordered note-list reads/writes for one template workout slot. */
@Dao
interface WorkoutLiftNoteDao {
    @Upsert
    suspend fun upsertAll(rows: List<WorkoutLiftNoteEntity>)

    @Query("SELECT * FROM workout_lift_notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: WorkoutLiftNoteId): WorkoutLiftNoteEntity?

    @Query("SELECT * FROM workout_lift_notes WHERE workout_lift_id = :workoutLiftId ORDER BY position ASC")
    suspend fun getForWorkoutLift(workoutLiftId: WorkoutLiftId): List<WorkoutLiftNoteEntity>

    @Query("SELECT id FROM workout_lift_notes WHERE workout_lift_id = :workoutLiftId ORDER BY position ASC")
    suspend fun idsForWorkoutLift(workoutLiftId: WorkoutLiftId): List<WorkoutLiftNoteId>

    /**
     * Pair-scoped delete (STR-127): removes only the rows whose ids the repository
     * resolved to one (slot, lift) pair, so other pairs of the slot stay stashed.
     * Pair resolution (including unscoped legacy rows) happens in Kotlin.
     */
    @Query("DELETE FROM workout_lift_notes WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<WorkoutLiftNoteId>)

    @Query("SELECT * FROM workout_lift_notes WHERE workout_lift_id IN (:workoutLiftIds)")
    suspend fun getForWorkoutLifts(workoutLiftIds: List<WorkoutLiftId>): List<WorkoutLiftNoteEntity>

    /** Note ids for every slot of one program (tombstones on hierarchy delete). */
    @Query(
        """
        SELECT n.id FROM workout_lift_notes n
        INNER JOIN workout_lifts wl ON wl.id = n.workout_lift_id
        INNER JOIN workouts w ON w.id = wl.workout_id
        WHERE w.program_id = :programId
        """,
    )
    suspend fun idsForProgram(programId: ProgramId): List<WorkoutLiftNoteId>

    @Query("DELETE FROM workout_lift_notes WHERE workout_lift_id = :workoutLiftId")
    suspend fun deleteForWorkoutLift(workoutLiftId: WorkoutLiftId)

    @Query("DELETE FROM workout_lift_notes WHERE id = :id")
    suspend fun deleteById(id: WorkoutLiftNoteId)

    @Query("SELECT * FROM workout_lift_notes WHERE pending_sync != ${SyncFlags.CLEAN} LIMIT :limit")
    suspend fun getPending(limit: Int = 500): List<WorkoutLiftNoteEntity>

    @Query(
        """
        UPDATE workout_lift_notes
        SET pending_sync = ${SyncFlags.CLEAN}
        WHERE id = :id AND updated_at_epoch_millis = :updatedAt
        """,
    )
    suspend fun markSyncedIfUnchanged(id: String, updatedAt: Long)
}

package com.stratum.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.lift.identity.LiftCatalogNoteId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutLiftNoteId

/** Ordered note-list reads/writes for one catalog lift. */
@Dao
interface LiftNoteDao {
    @Upsert
    suspend fun upsertAll(rows: List<LiftNoteEntity>)

    @Query("SELECT * FROM lift_notes WHERE lift_id = :liftId ORDER BY position ASC")
    suspend fun getForLift(liftId: LiftId): List<LiftNoteEntity>

    @Query("SELECT id FROM lift_notes WHERE lift_id = :liftId")
    suspend fun idsForLift(liftId: LiftId): List<LiftCatalogNoteId>

    @Query("DELETE FROM lift_notes WHERE lift_id = :liftId")
    suspend fun deleteForLift(liftId: LiftId)

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

    @Query("SELECT * FROM workout_lift_notes WHERE workout_lift_id = :workoutLiftId ORDER BY position ASC")
    suspend fun getForWorkoutLift(workoutLiftId: WorkoutLiftId): List<WorkoutLiftNoteEntity>

    @Query("SELECT id FROM workout_lift_notes WHERE workout_lift_id = :workoutLiftId")
    suspend fun idsForWorkoutLift(workoutLiftId: WorkoutLiftId): List<WorkoutLiftNoteId>

    @Query("SELECT * FROM workout_lift_notes WHERE workout_lift_id IN (:workoutLiftIds)")
    suspend fun getForWorkoutLifts(workoutLiftIds: List<WorkoutLiftId>): List<WorkoutLiftNoteEntity>

    @Query("DELETE FROM workout_lift_notes WHERE workout_lift_id = :workoutLiftId")
    suspend fun deleteForWorkoutLift(workoutLiftId: WorkoutLiftId)

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

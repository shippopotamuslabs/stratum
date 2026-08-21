package com.stratum.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.identity.LiftSessionNoteId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.identity.TrainingSessionId

@Dao
interface LiftSessionNoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LiftSessionNoteEntity)

    /**
     * Inserts [entity], replacing any other row for the same session+slot (different PK).
     * Returns the displaced id so callers can enqueue a cloud tombstone.
     */
    @Transaction
    suspend fun upsertMergingUniqueSlot(entity: LiftSessionNoteEntity): String? {
        val existing = getBySessionAndWorkoutLift(entity.sessionId, entity.workoutLiftId)
        val displaced =
            if (existing != null && existing.id != entity.id) {
                deleteById(existing.id)
                existing.id.value
            } else {
                null
            }
        upsert(entity)
        return displaced
    }

    @Query(
        """
        SELECT * FROM lift_session_notes
        WHERE session_id = :sessionId AND workout_lift_id = :workoutLiftId
        LIMIT 1
        """,
    )
    suspend fun getBySessionAndWorkoutLift(
        sessionId: TrainingSessionId,
        workoutLiftId: WorkoutLiftId,
    ): LiftSessionNoteEntity?

    @Query(
        """
        SELECT * FROM lift_session_notes
        WHERE session_id = :sessionId
        """,
    )
    suspend fun getForSession(sessionId: TrainingSessionId): List<LiftSessionNoteEntity>

    @Query(
        """
        DELETE FROM lift_session_notes
        WHERE session_id = :sessionId AND workout_lift_id = :workoutLiftId
        """,
    )
    suspend fun deleteBySessionAndWorkoutLift(
        sessionId: TrainingSessionId,
        workoutLiftId: WorkoutLiftId,
    )

    @Query("SELECT * FROM lift_session_notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: LiftSessionNoteId): LiftSessionNoteEntity?

    @Query("DELETE FROM lift_session_notes WHERE id = :id")
    suspend fun deleteById(id: LiftSessionNoteId)

    @Query(
        """
        SELECT n.id, n.session_id, n.lift_id, n.workout_lift_id, n.body,
               n.updated_at_epoch_millis, n.pending_sync,
               s.started_at_epoch_millis, s.workout_name_snapshot
        FROM lift_session_notes n
        INNER JOIN training_sessions s ON s.id = n.session_id
        WHERE n.lift_id = :liftId
          AND n.body != ''
          AND s.status = 'COMPLETED'
          AND n.session_id != :excludeSessionId
        ORDER BY s.started_at_epoch_millis DESC
        """,
    )
    suspend fun getPastNotesForLiftExcluding(
        liftId: LiftId,
        excludeSessionId: TrainingSessionId,
    ): List<LiftSessionNoteWithSession>

    @Query(
        """
        SELECT n.id, n.session_id, n.lift_id, n.workout_lift_id, n.body,
               n.updated_at_epoch_millis, n.pending_sync,
               s.started_at_epoch_millis, s.workout_name_snapshot
        FROM lift_session_notes n
        INNER JOIN training_sessions s ON s.id = n.session_id
        WHERE n.lift_id = :liftId
          AND n.body != ''
          AND s.status = 'COMPLETED'
        ORDER BY s.started_at_epoch_millis DESC
        """,
    )
    suspend fun getPastNotesForLift(liftId: LiftId): List<LiftSessionNoteWithSession>

    @Query(
        """
        UPDATE lift_session_notes
        SET lift_id = :targetLiftId,
            pending_sync = ${SyncFlags.PENDING_UPSERT},
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE lift_id IN (:sourceLiftIds)
        """,
    )
    suspend fun remapLiftIds(
        sourceLiftIds: List<LiftId>,
        targetLiftId: LiftId,
        updatedAtEpochMillis: Long,
    ): Int

    @Query("SELECT * FROM lift_session_notes WHERE pending_sync != ${SyncFlags.CLEAN} LIMIT :limit")
    suspend fun getPending(limit: Int = 500): List<LiftSessionNoteEntity>

    @Query(
        """
        UPDATE lift_session_notes
        SET pending_sync = ${SyncFlags.CLEAN}
        WHERE id = :id AND updated_at_epoch_millis = :updatedAt
        """,
    )
    suspend fun markSyncedIfUnchanged(id: String, updatedAt: Long)
}

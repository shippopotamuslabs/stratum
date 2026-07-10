package com.stratum.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingSessionDao {
    @Upsert
    suspend fun upsert(entity: TrainingSessionEntity)

    @Upsert
    suspend fun upsertAll(entities: List<TrainingSessionEntity>)

    @Query("SELECT * FROM training_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: TrainingSessionId): TrainingSessionEntity?

    @Query("SELECT * FROM training_sessions")
    suspend fun getAll(): List<TrainingSessionEntity>

    @Query("DELETE FROM training_sessions")
    suspend fun deleteAll()

    @Query("DELETE FROM training_sessions WHERE id = :id")
    suspend fun deleteById(id: TrainingSessionId)

    @Query("DELETE FROM training_sessions WHERE origin = 'IMPORT'")
    suspend fun deleteAllImported()

    @Query("SELECT * FROM training_sessions WHERE origin = 'IMPORT'")
    suspend fun getAllImported(): List<TrainingSessionEntity>

    @Query("SELECT * FROM training_sessions WHERE status = :status LIMIT 1")
    suspend fun getByStatus(status: TrainingSessionStatus): TrainingSessionEntity?

    @Query(
        """
        UPDATE training_sessions
        SET program_id = :newProgramId
        WHERE program_id = :oldProgramId
        """,
    )
    suspend fun reassignProgramId(
        oldProgramId: ProgramId,
        newProgramId: ProgramId,
    )

    @Query(
        """
        SELECT * FROM training_sessions
        WHERE status = :status
        LIMIT 1
        """,
    )
    fun observeByStatus(status: TrainingSessionStatus): Flow<TrainingSessionEntity?>

    @Query(
        """
        SELECT * FROM training_sessions
        WHERE program_id = :programId
          AND cycle_number_snapshot = :cycleNumber
          AND status = 'COMPLETED'
        """,
    )
    suspend fun getCompletedForCycle(programId: ProgramId, cycleNumber: Int): List<TrainingSessionEntity>

    @Query("SELECT * FROM training_sessions ORDER BY started_at_epoch_millis DESC")
    fun observeAll(): Flow<List<TrainingSessionEntity>>
}

@Dao
interface TrainingLoggedSetDao {
    @Upsert
    suspend fun upsert(entity: LoggedSetEntity)

    @Upsert
    suspend fun upsertAll(entities: List<LoggedSetEntity>)

    @Query("SELECT * FROM logged_sets WHERE sessionId = :sessionId")
    suspend fun getForSession(sessionId: TrainingSessionId): List<LoggedSetEntity>

    @Query("SELECT * FROM logged_sets")
    suspend fun getAll(): List<LoggedSetEntity>

    @Query("DELETE FROM logged_sets")
    suspend fun deleteAll()

    @Query(
        """
        SELECT * FROM logged_sets
        WHERE completedAtEpochMillis >= :windowStartEpochMillis
          AND completedAtEpochMillis <= :windowEndEpochMillis
        """,
    )
    suspend fun getInWindow(
        windowStartEpochMillis: Long,
        windowEndEpochMillis: Long,
    ): List<LoggedSetEntity>

    @Query(
        """
        SELECT ls.* FROM logged_sets ls
        INNER JOIN training_sessions ts ON ls.sessionId = ts.id
        WHERE ts.origin = 'IMPORT'
        """,
    )
    suspend fun getAllForImportedSessions(): List<LoggedSetEntity>

    @Query("DELETE FROM logged_sets WHERE id = :id")
    suspend fun deleteById(id: LoggedSetId)

    @Query(
        """
        SELECT ls.* FROM logged_sets ls
        INNER JOIN training_sessions ts ON ls.sessionId = ts.id
        WHERE ts.program_id = :programId
          AND ls.liftId = :liftId
          AND ls.setIndex = :setIndex
        ORDER BY
          CASE WHEN ts.workout_id = :workoutId THEN 0 ELSE 1 END,
          ts.started_at_epoch_millis DESC
        """,
    )
    suspend fun getHistoryForSlot(
        programId: ProgramId,
        workoutId: WorkoutId,
        liftId: LiftId,
        setIndex: Int,
    ): List<LoggedSetEntity>

    @Query(
        """
        DELETE FROM logged_sets WHERE sessionId IN (
            SELECT id FROM training_sessions WHERE origin = 'IMPORT'
        )
        """,
    )
    suspend fun deleteAllForImportedSessions()

    @Query("SELECT * FROM logged_sets WHERE liftId = :liftId AND isPersonalRecord = 1 LIMIT 1")
    suspend fun getPersonalRecordForLift(liftId: LiftId): LoggedSetEntity?

    @Query(
        """
        SELECT * FROM logged_sets
        WHERE liftId = :liftId
        ORDER BY completedAtEpochMillis DESC
        """,
    )
    suspend fun getSetsForLift(liftId: LiftId): List<LoggedSetEntity>

    /**
     * Earliest logged-set epoch per lift — the "how long have I been running this lift?" tenure
     * signal for the dashboard. Projection over existing columns only (no schema change).
     */
    @Query(
        """
        SELECT liftId AS liftId, MIN(completedAtEpochMillis) AS firstEpochMillis
        FROM logged_sets
        WHERE liftId IS NOT NULL
        GROUP BY liftId
        """,
    )
    suspend fun getFirstSetEpochPerLift(): List<LiftFirstSetRow>
}

/**
 * Projection row for [TrainingLoggedSetDao.getFirstSetEpochPerLift].
 */
data class LiftFirstSetRow(
    val liftId: String,
    val firstEpochMillis: Long,
)

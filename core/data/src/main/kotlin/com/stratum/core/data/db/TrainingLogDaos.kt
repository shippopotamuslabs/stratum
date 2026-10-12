package com.stratum.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.lift.identity.EquipmentId
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

    /**
     * Soft-unlink sessions from a deleted program template. Does **not** delete rows or
     * logged_sets; clears soft refs so history remains with name snapshots only.
     */
    @Query(
        """
        UPDATE training_sessions
        SET program_id = NULL,
            workout_id = NULL,
            pending_sync = ${SyncFlags.PENDING_UPSERT},
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE program_id = :programId
        """,
    )
    suspend fun clearSoftRefsForProgram(programId: ProgramId, updatedAtEpochMillis: Long)

    @Query("DELETE FROM training_sessions WHERE origin = 'IMPORT'")
    suspend fun deleteAllImported()

    @Query("SELECT * FROM training_sessions WHERE origin = 'IMPORT'")
    suspend fun getAllImported(): List<TrainingSessionEntity>

    @Query("SELECT * FROM training_sessions WHERE status = :status LIMIT 1")
    suspend fun getByStatus(status: TrainingSessionStatus): TrainingSessionEntity?

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

    @Query("SELECT * FROM training_sessions WHERE pending_sync != ${SyncFlags.CLEAN} LIMIT :limit")
    suspend fun getPending(limit: Int = 500): List<TrainingSessionEntity>

    @Query(
        """
        UPDATE training_sessions
        SET pending_sync = ${SyncFlags.CLEAN}
        WHERE id = :id AND updated_at_epoch_millis = :updatedAt
        """,
    )
    suspend fun markSyncedIfUnchanged(id: String, updatedAt: Long)
}

@Dao
@Suppress("TooManyFunctions")
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

    /**
     * Sets with [LoggedSetEntity.completedAtEpochMillis] in the inclusive window.
     * Uses index `logged_sets_completedAtEpochMillis` (see [LoggedSetEntity] indices).
     */
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

    /**
     * All-time sets for lifts that can credit [muscleBit] under the same rules as volume credit:
     * - stored primary/secondary muscle tags on the lift, **or**
     * - press patterns when [pressPatternBits] is non-zero (policy Tricep secondary), **or**
     * - pull patterns when [pullPatternBits] is non-zero (policy Bicep secondary).
     *
     * Pass zero for press/pull bits when those OR branches should not apply.
     * [muscleBit] / pattern bits use `1 shl enum.ordinal` (same as [LiftEntity] columns).
     */
    @Query(
        """
        SELECT DISTINCT ls.* FROM logged_sets ls
        INNER JOIN lifts l ON ls.liftId = l.id
        WHERE (l.primaryMusclesBitmask & :muscleBit) != 0
           OR (l.secondaryMusclesBitmask & :muscleBit) != 0
           OR (
                :pressPatternBits != 0
                AND (l.movementPatternsBitmask & :pressPatternBits) != 0
           )
           OR (
                :pullPatternBits != 0
                AND (l.movementPatternsBitmask & :pullPatternBits) != 0
           )
        """,
    )
    suspend fun getForMuscleCredit(
        muscleBit: Int,
        pressPatternBits: Int,
        pullPatternBits: Int,
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

    /**
     * Progression history for a slot position: same lift + same set index across **all**
     * sessions (live program workouts **and** imported history). Intentionally does **not**
     * filter by program id — imported sessions use sentinel program `import-history`, and
     * soft-unlinked sessions may have a null program id after template delete. Ordering is
     * applied in [com.stratum.core.data.repository.ProgressionHistoryRepository].
     */
    /**
     * Completed sessions only — in-progress live logs must not appear as "previous"
     * or seed progression math (resume mid-session would otherwise overwrite Prev
     * with this session's sets).
     */
    @Query(
        """
        SELECT ls.* FROM logged_sets ls
        INNER JOIN training_sessions ts ON ls.sessionId = ts.id
        WHERE ls.liftId = :liftId
          AND ls.setIndex = :setIndex
          AND ts.status = 'COMPLETED'
        ORDER BY
          CASE WHEN ts.workout_id = :workoutId THEN 0 ELSE 1 END,
          ts.started_at_epoch_millis DESC
        """,
    )
    suspend fun getHistoryForSlot(
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

    @Query(
        """
        SELECT * FROM logged_sets
        WHERE liftId IN (:liftIds)
        """,
    )
    suspend fun getSetsForLifts(liftIds: List<LiftId>): List<LoggedSetEntity>

    /**
     * Reassigns all sets from [sourceLiftIds] onto [targetLiftId], rewrites snapshot names, and
     * clears personal-record flags (null) so the unique (liftId, equipmentId, isPersonalRecord) index is safe.
     */
    @Query(
        """
        UPDATE logged_sets
        SET liftId = :targetLiftId,
            exerciseNameRaw = :targetName,
            liftName = :targetName,
            isPersonalRecord = NULL,
            pending_sync = ${SyncFlags.PENDING_UPSERT},
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE liftId IN (:sourceLiftIds)
        """,
    )
    suspend fun reassignSetsToLift(
        sourceLiftIds: List<LiftId>,
        targetLiftId: LiftId,
        targetName: String,
        updatedAtEpochMillis: Long,
    ): Int

    /** Clears PR flags on [liftId] (pre-collapse demotion for the survivor). */
    @Query(
        """
        UPDATE logged_sets
        SET isPersonalRecord = NULL,
            pending_sync = ${SyncFlags.PENDING_UPSERT},
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE liftId = :liftId AND isPersonalRecord = 1
        """,
    )
    suspend fun clearPersonalRecordsForLift(
        liftId: LiftId,
        updatedAtEpochMillis: Long,
    ): Int

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

    @Query("SELECT * FROM logged_sets WHERE id = :id LIMIT 1")
    suspend fun getById(id: LoggedSetId): LoggedSetEntity?

    @Query("SELECT * FROM logged_sets WHERE pending_sync != ${SyncFlags.CLEAN} LIMIT :limit")
    suspend fun getPending(limit: Int = 500): List<LoggedSetEntity>

    @Query(
        """
        UPDATE logged_sets
        SET pending_sync = ${SyncFlags.CLEAN}
        WHERE id = :id AND updated_at_epoch_millis = :updatedAt
        """,
    )
    suspend fun markSyncedIfUnchanged(id: String, updatedAt: Long)

    /**
     * Clears other personal-record flags for the ([liftId], [equipmentId]) pair so remote
     * PR apply cannot violate the unique (liftId, equipmentId, isPersonalRecord) index.
     * PR identity is per pair since STR-123: sibling-equipment PRs are independent and
     * must not be demoted.
     *
     * Demotions use PENDING_UPSERT + a non-regressing clock (same pattern as
     * [com.stratum.core.data.db.ProgramDao.demoteOtherActivesPending]) so dual-PR cloud
     * heals on upload without rewinding a newer local LWW clock.
     */
    @Query(
        """
        UPDATE logged_sets
        SET isPersonalRecord = 0,
            pending_sync = ${SyncFlags.PENDING_UPSERT},
            updated_at_epoch_millis = CASE
                WHEN updated_at_epoch_millis < :minUpdatedAtEpochMillis
                THEN :minUpdatedAtEpochMillis
                ELSE updated_at_epoch_millis
            END
        WHERE liftId = :liftId
          AND equipmentId IS :equipmentId
          AND isPersonalRecord = 1
          AND id != :exceptId
        """,
    )
    suspend fun demotePersonalRecordsForLift(
        liftId: LiftId,
        equipmentId: EquipmentId?,
        exceptId: LoggedSetId,
        minUpdatedAtEpochMillis: Long,
    )
}

/**
 * Projection row for [TrainingLoggedSetDao.getFirstSetEpochPerLift].
 */
data class LiftFirstSetRow(
    val liftId: String,
    val firstEpochMillis: Long,
)

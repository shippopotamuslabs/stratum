package com.stratum.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.training.identity.ProgramChangeEventId

@Dao
interface ProgramChangeEventDao {
    @Upsert
    suspend fun upsert(entity: ProgramChangeEventEntity)

    @Query("SELECT * FROM program_change_events WHERE id = :id LIMIT 1")
    suspend fun getById(id: ProgramChangeEventId): ProgramChangeEventEntity?

    @Query("DELETE FROM program_change_events WHERE id = :id")
    suspend fun deleteById(id: ProgramChangeEventId)

    @Query(
        """
        SELECT * FROM program_change_events
        WHERE program_id = :programId
        ORDER BY occurred_at_epoch_millis ASC
        """,
    )
    suspend fun getForProgram(programId: ProgramId): List<ProgramChangeEventEntity>

    @Query("SELECT COUNT(*) FROM program_change_events WHERE program_id = :programId")
    suspend fun countForProgram(programId: ProgramId): Int

    @Query("SELECT id FROM program_change_events WHERE program_id = :programId")
    suspend fun idsForProgram(programId: ProgramId): List<String>

    @Query("SELECT * FROM program_change_events WHERE pending_sync != ${SyncFlags.CLEAN} LIMIT :limit")
    suspend fun getPending(limit: Int = 500): List<ProgramChangeEventEntity>

    @Query(
        """
        UPDATE program_change_events
        SET pending_sync = ${SyncFlags.CLEAN}
        WHERE id = :id AND updated_at_epoch_millis = :updatedAt
        """,
    )
    suspend fun markSyncedIfUnchanged(id: String, updatedAt: Long)
}

package com.stratum.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stratum.domain.program.identity.ProgramId

@Dao
interface ProgramChangeEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ProgramChangeEventEntity)

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
}

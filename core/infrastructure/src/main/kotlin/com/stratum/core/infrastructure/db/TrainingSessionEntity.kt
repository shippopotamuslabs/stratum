package com.stratum.core.infrastructure.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stratum.core.infrastructure.sync.SyncFlags
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId

/**
 * Training session ledger. Rows are permanent history.
 *
 * Soft refs [programId] / [workoutId] are **not** Room foreign keys: program template rewrite
 * (`deleteForProgram` + re-insert workouts) would otherwise SET_NULL every session's workout_id
 * on routine saves. Soft refs are cleared explicitly when a program template is deleted
 * ([clearSoftRefsForProgram]). Display uses [programNameSnapshot] / [workoutNameSnapshot].
 *
 * Schema v1 reinstall-only (AGENTS.md §4.5) — no migration.
 */
@Entity(
    tableName = "training_sessions",
    indices = [
        Index(value = ["program_id", "cycle_number_snapshot"]),
        Index(value = ["program_id", "status"]),
        Index(value = ["workout_id"]),
    ],
)
data class TrainingSessionEntity(
    @PrimaryKey val id: TrainingSessionId,
    @ColumnInfo(name = "program_id") val programId: ProgramId?,
    @ColumnInfo(name = "workout_id") val workoutId: WorkoutId?,
    val status: TrainingSessionStatus,
    val kind: TrainingSessionKind,
    val origin: TrainingSessionOrigin,
    @ColumnInfo(name = "cycle_number_snapshot") val cycleNumberSnapshot: Int,
    @ColumnInfo(name = "weekday_snapshot") val weekdaySnapshot: Weekday?,
    @ColumnInfo(name = "cycle_slot_index_snapshot") val cycleSlotIndexSnapshot: Int? = null,
    @ColumnInfo(name = "started_at_epoch_millis") val startedAtEpochMillis: Long,
    @ColumnInfo(name = "ended_at_epoch_millis") val endedAtEpochMillis: Long?,
    @ColumnInfo(name = "source_app") val sourceApp: String?,
    @ColumnInfo(name = "program_name_snapshot") val programNameSnapshot: String? = null,
    @ColumnInfo(name = "workout_name_snapshot") val workoutNameSnapshot: String? = null,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = 0L,
    @ColumnInfo(name = "pending_sync")
    val pendingSync: Int = SyncFlags.CLEAN,
)

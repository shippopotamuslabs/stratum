package com.stratum.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.identity.ProgramChangeEventId

@Entity(
    tableName = "program_change_events",
    foreignKeys = [
        ForeignKey(
            entity = ProgramEntity::class,
            parentColumns = ["id"],
            childColumns = ["program_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["program_id", "occurred_at_epoch_millis"])],
)
data class ProgramChangeEventEntity(
    @PrimaryKey val id: ProgramChangeEventId,
    @ColumnInfo(name = "program_id") val programId: ProgramId,
    @ColumnInfo(name = "event_type") val eventType: ProgramChangeEventType,
    @ColumnInfo(name = "occurred_at_epoch_millis") val occurredAtEpochMillis: Long,
    /**
     * Self-describing event payload as JSON ([ProgramChangeEventPayloadDto], v6+). The
     * source of truth for every row written by this version; `null` only on pre-v6 rows,
     * which readers reconstruct from the frozen legacy columns below.
     *
     * New event types extend the DTO — never add another payload column to this table.
     */
    @ColumnInfo(name = "payload_json") val payloadJson: String? = null,
    /**
     * Pre-v6 flat payload columns. Frozen: read-only fallback for rows written before the
     * JSON payload existed. New writes leave these `null`; new event types must not
     * populate them.
     */
    @ColumnInfo(name = "workout_lift_id") val workoutLiftId: WorkoutLiftId? = null,
    @ColumnInfo(name = "previous_lift_id") val previousLiftId: LiftId? = null,
    @ColumnInfo(name = "new_lift_id") val newLiftId: LiftId? = null,
    @ColumnInfo(name = "workout_id") val workoutId: WorkoutId? = null,
    @ColumnInfo(name = "completed_cycle_number") val completedCycleNumber: Int? = null,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = 0L,
    @ColumnInfo(name = "pending_sync")
    val pendingSync: Int = SyncFlags.CLEAN,
)

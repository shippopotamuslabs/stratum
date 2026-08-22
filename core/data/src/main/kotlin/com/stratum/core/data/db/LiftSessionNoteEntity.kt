package com.stratum.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.identity.LiftSessionNoteId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.identity.TrainingSessionId

/**
 * Transient session note for one lift slot. Historical after the session completes.
 * CASCADE-deleted with the training session (abandon wipes in-progress notes).
 * [workoutLiftId] is a soft ref so overlay-added slots can persist without a template row.
 */
@Entity(
    tableName = "lift_session_notes",
    foreignKeys = [
        ForeignKey(
            entity = TrainingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["session_id", "workout_lift_id"], unique = true),
        Index("lift_id"),
        Index("session_id"),
    ],
)
data class LiftSessionNoteEntity(
    @PrimaryKey val id: LiftSessionNoteId,
    @ColumnInfo(name = "session_id") val sessionId: TrainingSessionId,
    @ColumnInfo(name = "lift_id") val liftId: LiftId,
    @ColumnInfo(name = "workout_lift_id") val workoutLiftId: WorkoutLiftId,
    val body: String,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = 0L,
    @ColumnInfo(name = "pending_sync")
    val pendingSync: Int = SyncFlags.CLEAN,
)

/** Join row for past-session note history (completed sessions). */
data class LiftSessionNoteWithSession(
    @Embedded val note: LiftSessionNoteEntity,
    @ColumnInfo(name = "started_at_epoch_millis") val startedAtEpochMillis: Long,
    @ColumnInfo(name = "workout_name_snapshot") val workoutNameSnapshot: String?,
)

package com.stratum.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.lift.identity.LiftCatalogNoteId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutLiftNoteId

/**
 * One row of a lift's catalog note list (multi-note successor of `lifts.pinned_note`,
 * which stays as a first-note mirror until retirement). CASCADE-deleted with the lift.
 */
@Entity(
    tableName = "lift_notes",
    foreignKeys = [
        ForeignKey(
            entity = LiftEntity::class,
            parentColumns = ["id"],
            childColumns = ["lift_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("lift_id"),
        Index(value = ["lift_id", "position"]),
    ],
)
data class LiftNoteEntity(
    @PrimaryKey val id: LiftCatalogNoteId,
    @ColumnInfo(name = "lift_id") val liftId: LiftId,
    val body: String,
    val position: Int = 0,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long = 0L,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = 0L,
    @ColumnInfo(name = "pending_sync")
    val pendingSync: Int = SyncFlags.CLEAN,
)

/**
 * One row of a template slot's workout note list (multi-note successor of
 * `workout_lifts.pinned_note`, which stays as a first-note mirror until retirement).
 * CASCADE-deleted with the slot, so program-hierarchy replaces drop removed slots' notes.
 */
@Entity(
    tableName = "workout_lift_notes",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutLiftEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_lift_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("workout_lift_id"),
        Index(value = ["workout_lift_id", "position"]),
    ],
)
data class WorkoutLiftNoteEntity(
    @PrimaryKey val id: WorkoutLiftNoteId,
    @ColumnInfo(name = "workout_lift_id") val workoutLiftId: WorkoutLiftId,
    val body: String,
    val position: Int = 0,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long = 0L,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = 0L,
    @ColumnInfo(name = "pending_sync")
    val pendingSync: Int = SyncFlags.CLEAN,
)

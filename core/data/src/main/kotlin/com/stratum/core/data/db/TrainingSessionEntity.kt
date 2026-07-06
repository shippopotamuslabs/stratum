package com.stratum.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId

@Entity(
    tableName = "training_sessions",
    foreignKeys = [
        ForeignKey(
            entity = ProgramEntity::class,
            parentColumns = ["id"],
            childColumns = ["program_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["program_id", "cycle_number_snapshot"]),
        Index(value = ["program_id", "status"]),
    ],
)
data class TrainingSessionEntity(
    @PrimaryKey val id: TrainingSessionId,
    @ColumnInfo(name = "program_id") val programId: ProgramId,
    @ColumnInfo(name = "workout_id") val workoutId: WorkoutId?,
    val status: TrainingSessionStatus,
    val kind: TrainingSessionKind,
    val origin: TrainingSessionOrigin,
    @ColumnInfo(name = "cycle_number_snapshot") val cycleNumberSnapshot: Int,
    @ColumnInfo(name = "weekday_snapshot") val weekdaySnapshot: Weekday?,
    @ColumnInfo(name = "started_at_epoch_millis") val startedAtEpochMillis: Long,
    @ColumnInfo(name = "ended_at_epoch_millis") val endedAtEpochMillis: Long?,
    @ColumnInfo(name = "source_app") val sourceApp: String?,
)

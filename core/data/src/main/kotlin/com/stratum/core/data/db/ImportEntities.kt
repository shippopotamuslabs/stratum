package com.stratum.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.importing.ImportJobStatus
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.importing.identity.ImportJobId
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.identity.WorkoutLiftId

@Entity(tableName = "import_jobs")
data class ImportJobEntity(
    @PrimaryKey val id: ImportJobId,
    val status: ImportJobStatus,
    val sampleRef: String?,
    val fullRef: String?,
    val scriptRef: String?,
    val errorMessage: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "logged_sets",
    foreignKeys = [
        ForeignKey(
            entity = TrainingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sessionId"),
        Index("completedAtEpochMillis"),
        Index(value = ["workoutLiftId", "liftId", "setIndex"]),
        Index(value = ["liftId", "isPersonalRecord"], unique = true),
    ],
)
data class LoggedSetEntity(
    @PrimaryKey val id: LoggedSetId,
    val sessionId: TrainingSessionId,
    val exerciseNameRaw: String,
    val liftId: LiftId?,
    val setIndex: Int,
    val weightAmount: Double,
    val weightUnit: String,
    val reps: Int,
    val rir: Int?,
    val completedAtEpochMillis: Long,
    val liftName: String = "",
    val movementPatternsBitmask: Int = 0,
    val primaryMusclesBitmask: Int = 0,
    val secondaryMusclesBitmask: Int = 0,
    val fatigueProfile: FatigueProfile = FatigueProfile.Medium,
    val muscleLengthBias: MuscleLengthBias = MuscleLengthBias.MidRange,
    val workoutLiftId: WorkoutLiftId? = null,
    val progressionScheme: ProgressionScheme? = null,
    val setType: SetType? = null,
    val repMin: Int? = null,
    val repMax: Int? = null,
    val rirGoal: Int? = null,
    val isPersonalRecord: Boolean? = null,
    val estimatedOneRm: Double? = null,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = 0L,
    @ColumnInfo(name = "pending_sync")
    val pendingSync: Int = SyncFlags.CLEAN,
)

@Entity(tableName = "lift_training_insights")
data class LiftTrainingInsightsEntity(
    @PrimaryKey val liftId: LiftId,
    val estimatedOneRm: Double?,
    val weightUnit: String?,
    val fatigueDecaySlope: Double?,
    val fatigueDecayIntercept: Double?,
    val sessionCount: Int,
    val progressTrend: String?,
    val lastPerformedAtEpochMillis: Long?,
)

@Entity(
    tableName = "superset_affinities",
    indices = [Index(value = ["liftA", "liftB"], unique = true)],
)
data class SupersetAffinityEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val liftA: String,
    val liftB: String,
    val medianRestSeconds: Double,
    val coOccurrenceCount: Int,
    val confidence: Double,
)

@Entity(tableName = "muscle_volume_baselines")
data class MuscleVolumeBaselineEntity(
    @PrimaryKey val muscle: MuscleGroup,
    val meanWeeklyHardSets: Double,
    val p90WeeklyHardSets: Double,
    val trendSlope: Double?,
)

@Entity(tableName = "import_analysis_metadata")
data class ImportAnalysisMetadataEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val windowStartEpochMillis: Long,
    val windowEndEpochMillis: Long,
    val provenLiftIdsCsv: String,
    val unmatchedExerciseNamesCsv: String,
    val totalSessions: Int,
    val totalSets: Int,
    val extractedAtEpochMillis: Long = 0,
    val pipelineVersion: String? = null,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

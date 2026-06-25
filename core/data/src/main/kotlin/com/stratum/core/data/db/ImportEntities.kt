package com.stratum.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.stratum.domain.importing.ImportJobStatus
import com.stratum.domain.importing.identity.ImportJobId

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

@Entity(tableName = "historical_sessions")
data class HistoricalSessionEntity(
    @PrimaryKey val id: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val sourceApp: String?,
)

@Entity(tableName = "logged_sets")
data class LoggedSetEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val exerciseNameRaw: String,
    val liftId: String?,
    val setIndex: Int,
    val weightAmount: Double,
    val weightUnit: String,
    val reps: Int,
    val rir: Int?,
    val completedAtEpochMillis: Long,
)

@Entity(tableName = "lift_training_insights")
data class LiftTrainingInsightsEntity(
    @PrimaryKey val liftId: String,
    val estimatedOneRm: Double?,
    val weightUnit: String?,
    val fatigueDecaySlope: Double?,
    val fatigueDecayIntercept: Double?,
    val sessionCount: Int,
    val progressTrend: String?,
    val lastPerformedAtEpochMillis: Long?,
)

@Entity(tableName = "superset_affinities")
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
    @PrimaryKey val muscle: String,
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
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

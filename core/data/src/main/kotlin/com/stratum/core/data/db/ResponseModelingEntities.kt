package com.stratum.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.stratum.domain.importing.IntervalMethod
import com.stratum.domain.importing.LandmarkConfidenceTier
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.modeling.OutcomeModelType

@Entity(
    tableName = "muscle_weekly_stimulus",
    primaryKeys = ["muscle", "weekStartEpochMillis"],
)
data class MuscleWeeklyStimulusEntity(
    val muscle: MuscleGroup,
    val weekStartEpochMillis: Long,
    val hardSets: Double,
    val sessions: Int,
    val meanRir: Double?,
    val intensityProxy: Double?,
    val compositionHash: String,
    val deloadFlag: Boolean,
    val qualityFlagsCsv: String,
)

@Entity(
    tableName = "lift_weekly_performance",
    primaryKeys = ["liftId", "weekStartEpochMillis"],
)
data class LiftWeeklyPerformanceEntity(
    val liftId: String,
    val weekStartEpochMillis: Long,
    val peakE1rm: Double,
    val smoothedE1rm: Double,
    val logE1rm: Double,
    val hardSets: Double,
    val sessions: Int,
    val meanRir: Double?,
    val reliability: Double,
    val qualityFlagsCsv: String,
)

@Entity(
    tableName = "muscle_weekly_outcome_indices",
    primaryKeys = ["muscle", "weekStartEpochMillis"],
)
data class MuscleWeeklyOutcomeIndexEntity(
    val muscle: MuscleGroup,
    val weekStartEpochMillis: Long,
    val indexValue: Double,
    val indexStdError: Double?,
    val contributingLiftCount: Int,
    val effectiveLiftCount: Int,
    val primaryCoverage: Double,
    val compositionHash: String,
    val compositionDrift: Double,
    val qualityFlagsCsv: String,
)

@Entity(
    tableName = "muscle_dose_response_points",
    primaryKeys = ["muscle", "weekStartEpochMillis", "lagWeeks"],
)
data class MuscleDoseResponsePointEntity(
    val muscle: MuscleGroup,
    val weekStartEpochMillis: Long,
    val dose: Double,
    val response: Double,
    val lagWeeks: Int,
    val responseWindowWeeks: Int,
)

@Entity(tableName = "muscle_volume_response_snapshots")
data class MuscleVolumeResponseSnapshotEntity(
    @PrimaryKey val muscle: MuscleGroup,
    val retentionFloorMv: Double,
    val paretoVolumePv: Double,
    val recoverabilityCeilingRc: Double,
    val growthEntryFloor: Double,
    val observedDoseMin: Double,
    val observedDoseMax: Double,
    val observedDoseMedian: Double,
    val productiveBandMin: Double?,
    val productiveBandMax: Double?,
    val posture: com.stratum.domain.response.modeling.VolumePosture,
    val pvIntervalLow: Double,
    val pvIntervalHigh: Double,
    val pvIntervalMethod: IntervalMethod,
    val confidenceTier: LandmarkConfidenceTier,
    val outcomeModelType: OutcomeModelType,
    val diagnosticAnchorLiftId: String?,
)

@Entity(tableName = "muscle_response_model_diagnostics")
data class MuscleResponseModelDiagnosticEntity(
    @PrimaryKey val muscle: MuscleGroup,
    val selectedLagWeeks: Int,
    val responseWindowWeeks: Int,
    val pairedPointCount: Int,
    val volumeBinCount: Int,
    val indexWeekCount: Int,
    val peakDoseCoverage: Double,
    val compositionStability: Double,
    val residualError: Double?,
    val predictiveScore: Double?,
    val anchorAgreement: Boolean?,
    val rejectionReasonsCsv: String,
    val confidenceScore: Double,
)

package com.stratum.domain.response

import com.stratum.domain.importing.LiftTrainingInsights
import com.stratum.domain.importing.MuscleVolumeBaseline
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.identity.EpochId
import com.stratum.domain.response.identity.ExperimentId
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import java.time.Instant

enum class EpochSplitReason {
    SessionGap,
    PrimaryLiftChange,
    LiftChurn,
    EquipmentShift,
    ChangePoint,
    WindowEnd,
}

enum class OutcomeMetric {
    EstimatedOneRmTrend,
    RirAdjustedE1rmTrend,
    WeeklyPeakE1rm,
    ProgressSlope,
}

enum class EvidenceLevel {
    Proven,
    Hypothesis,
    InsufficientData,
}

enum class ExperimentStatus {
    Scheduled,
    Active,
    Completed,
    Cancelled,
}

enum class TrainingSignalQualityFlag {
    MissingRir,
    HighRirWarmupExcluded,
    LowRelativeLoadWarmupExcluded,
    MixedUnits,
    OutlierWinsorized,
    SuspiciousE1rmJump,
    SparseWeek,
    EquipmentShiftSuspected,
}

data class CleanTrainingSet(
    val sourceSet: LoggedSet,
    val normalizedWeight: Double,
    val estimatedOneRm: Double,
    val rirAdjustedEstimatedOneRm: Double?,
    val isWorkingSet: Boolean,
    val qualityFlags: Set<TrainingSignalQualityFlag>,
)

data class WeeklyTrainingSignal(
    val weekStart: Instant,
    val liftId: LiftId?,
    val muscle: MuscleGroup?,
    val hardSets: Double,
    val sessions: Int,
    val meanRir: Double?,
    val intensityProxy: Double?,
    val peakEstimatedOneRm: Double?,
    val smoothedEstimatedOneRm: Double?,
    val qualityFlags: Set<TrainingSignalQualityFlag>,
)

data class WeeklyPoint(
    val weekStart: Instant,
    val value: Double,
)

data class RegressionReliability(
    val rSquared: Double?,
    val outlierCount: Int,
    val completenessScore: Double,
)

data class WeeklySetRange(
    val min: Double,
    val max: Double,
)

data class Epoch(
    val id: EpochId,
    val start: Instant,
    val end: Instant,
    val anchorLiftIds: Set<LiftId>,
    val splitReason: EpochSplitReason,
)

data class StimulusDescriptor(
    val epochId: EpochId?,
    val experimentId: ExperimentId?,
    val muscle: MuscleGroup?,
    val pattern: MovementPattern?,
    val liftId: LiftId?,
    val weeklyHardSets: Double,
    val sessionsPerWeek: Double,
    val intensityProxy: Double?,
)

data class OutcomeDescriptor(
    val anchorLiftId: LiftId,
    val metric: OutcomeMetric,
    val value: Double,
    val windowWeeks: Int,
    val reliability: RegressionReliability,
)

data class VolumeResponseObservation(
    val muscle: MuscleGroup,
    val epochId: EpochId,
    val anchorLiftId: LiftId,
    val stimulus: StimulusDescriptor,
    val outcome: OutcomeDescriptor,
    val correlationSlope: Double,
    val confidenceScore: Double,
    val evidence: EvidenceLevel,
    val weeklyPointsUsed: Int,
    val epochWeeksUsed: Int,
    val observedWeeklySetRange: WeeklySetRange,
    val bestObservedWeeklySetRange: WeeklySetRange?,
)

data class LiftEffectivenessObservation(
    val pattern: MovementPattern,
    val liftId: LiftId,
    val epochId: EpochId,
    val effectivenessScore: Double,
    val peerLiftCount: Int,
    val rank: Int,
    val evidence: EvidenceLevel,
    val confidenceScore: Double,
)

data class ExperimentBlock(
    val id: ExperimentId,
    val prescribed: StimulusDescriptor,
    val outcomeWindowStart: Instant,
    val outcomeWindowEnd: Instant,
    val status: ExperimentStatus,
)

data class ResponseExtractionInput(
    val sessions: List<TrainingSession>,
    val sets: List<LoggedSet>,
    val lifts: List<Lift>,
    val provenLiftIds: Set<LiftId>,
    val specializeMuscles: Set<MuscleGroup>,
    val liftInsights: List<LiftTrainingInsights>,
    val volumeBaselines: List<MuscleVolumeBaseline>,
    val analysisWindowStart: Instant,
    val analysisWindowEnd: Instant,
    val activeExperiments: List<ExperimentBlock>,
)

data class ResponseExtractionOutput(
    val epochs: List<Epoch>,
    val volumeResponses: List<VolumeResponseObservation>,
    val liftEffectiveness: List<LiftEffectivenessObservation>,
    val deloadWeeksExcluded: Set<Instant>,
    val modeling: com.stratum.domain.response.modeling.ResponseModelingOutput? = null,
)

data class ExtractResponseProfileSummary(
    val epochsSegmented: Int,
    val musclesWithVolumeResponse: Int,
    val patternsWithEffectiveness: Int,
    val experimentsRecorded: Int,
)

data class ResponseProfileMetadata(
    val epochsAnalyzed: Int,
    val analysisWindowStart: Instant,
    val analysisWindowEnd: Instant,
    val lastExtractedAt: Instant,
    val pipelineVersion: String? = null,
)

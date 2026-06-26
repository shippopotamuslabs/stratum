package com.stratum.domain.response.modeling

import com.stratum.domain.importing.LandmarkConfidenceInterval
import com.stratum.domain.importing.LandmarkConfidenceTier
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.TrainingSignalQualityFlag
import java.time.Instant

/** How the muscle outcome series was derived for landmark estimation. */
enum class OutcomeModelType {
    MuscleIndex,
    BestAnchor,
    PriorOnly,
}

/** Whether MRV came from observed tolerance, bounded extrapolation, or priors. */
enum class MrvSource {
    Observed,
    BoundedExtrapolation,
    PriorDominant,
}

data class MuscleWeeklyStimulus(
    val muscle: MuscleGroup,
    val weekStart: Instant,
    val hardSets: Double,
    val sessions: Int,
    val meanRir: Double?,
    val intensityProxy: Double?,
    val compositionHash: String,
    val deloadFlag: Boolean,
    val qualityFlags: Set<TrainingSignalQualityFlag>,
)

data class LiftWeeklyPerformance(
    val liftId: LiftId,
    val weekStart: Instant,
    val peakE1rm: Double,
    val smoothedE1rm: Double,
    val logE1rm: Double,
    val hardSets: Double,
    val sessions: Int,
    val meanRir: Double?,
    val reliability: Double,
    val qualityFlags: Set<TrainingSignalQualityFlag>,
)

data class MuscleWeeklyOutcomeIndex(
    val muscle: MuscleGroup,
    val weekStart: Instant,
    val indexValue: Double,
    val indexStdError: Double?,
    val contributingLiftCount: Int,
    val effectiveLiftCount: Int,
    val primaryCoverage: Double,
    val compositionHash: String,
    val compositionDrift: Double,
    val qualityFlags: Set<TrainingSignalQualityFlag>,
)

data class MuscleDoseResponsePoint(
    val muscle: MuscleGroup,
    val weekStart: Instant,
    val dose: Double,
    val response: Double,
    val lagWeeks: Int,
    val responseWindowWeeks: Int,
)

data class MuscleVolumeLandmarkEstimate(
    val muscle: MuscleGroup,
    val estimatedMev: Double,
    val estimatedMav: Double,
    val estimatedMrv: Double,
    val estimatedPv: Double,
    val mevInterval: LandmarkConfidenceInterval,
    val mavInterval: LandmarkConfidenceInterval,
    val mrvInterval: LandmarkConfidenceInterval,
    val pvInterval: LandmarkConfidenceInterval,
    val confidenceTier: LandmarkConfidenceTier,
    val outcomeModelType: OutcomeModelType,
    val mrvSource: MrvSource,
    val observedDoseMin: Double,
    val observedDoseMax: Double,
    val diagnosticAnchorLiftId: LiftId?,
)

data class MuscleResponseModelDiagnostic(
    val muscle: MuscleGroup,
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
    val rejectionReasons: List<String>,
    val confidenceScore: Double,
)

data class MuscleResponseModelResult(
    val landmarks: MuscleVolumeLandmarkEstimate,
    val diagnostics: MuscleResponseModelDiagnostic,
    val dosePoints: List<MuscleDoseResponsePoint>,
)

data class ResponseModelingOutput(
    val weeklyStimulus: List<MuscleWeeklyStimulus>,
    val liftPerformance: List<LiftWeeklyPerformance>,
    val outcomeIndices: List<MuscleWeeklyOutcomeIndex>,
    val landmarkEstimates: List<MuscleVolumeLandmarkEstimate>,
    val diagnostics: List<MuscleResponseModelDiagnostic>,
    val dosePoints: List<MuscleDoseResponsePoint>,
)

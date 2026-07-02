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

/** Marginal-returns posture from passive dose–response history (not a measured optimum). */
enum class VolumePosture {
    RoomToGrow,
    Holding,
    Saturated,
    Unclear,
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

data class MuscleVolumeResponseSnapshot(
    val muscle: MuscleGroup,
    val retentionFloorMv: Double,
    val paretoVolumePv: Double,
    val recoverabilityCeilingRc: Double,
    val growthEntryFloor: Double,
    val observedDoseMin: Double,
    val observedDoseMax: Double,
    val observedDoseMedian: Double,
    val productiveBandMin: Double?,
    val productiveBandMax: Double?,
    val posture: VolumePosture,
    val pvInterval: LandmarkConfidenceInterval,
    val confidenceTier: LandmarkConfidenceTier,
    val outcomeModelType: OutcomeModelType,
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
    val snapshot: MuscleVolumeResponseSnapshot,
    val diagnostics: MuscleResponseModelDiagnostic,
    val dosePoints: List<MuscleDoseResponsePoint>,
)

data class ResponseModelingOutput(
    val weeklyStimulus: List<MuscleWeeklyStimulus>,
    val liftPerformance: List<LiftWeeklyPerformance>,
    val outcomeIndices: List<MuscleWeeklyOutcomeIndex>,
    val volumeSnapshots: List<MuscleVolumeResponseSnapshot>,
    val diagnostics: List<MuscleResponseModelDiagnostic>,
    val dosePoints: List<MuscleDoseResponsePoint>,
)

package com.stratum.domain.importing

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.EvidenceLevel

data class HistoricalImportSummary(
    val totalSessions: Int,
    val totalSets: Int,
    val analysisWeeks: Int,
    val enabledLiftCount: Int,
    val provenLiftCount: Int,
    val customLiftsCreated: Int,
    val liftsWithInsights: Int,
    val supersetPairs: Int,
    val volumeHighlights: List<MuscleVolumeHighlight>,
    val effectiveLifts: List<EffectiveLiftHighlight>,
    val volumeResponses: List<VolumeResponseHighlight>,
)

data class MuscleVolumeHighlight(
    val muscle: MuscleGroup,
    val retentionFloorMv: Double,
    val paretoVolumePv: Double,
    val recoverabilityCeilingRc: Double,
    val observedDoseMin: Double,
    val observedDoseMax: Double,
    val observedDoseMedian: Double,
    val posture: com.stratum.domain.response.modeling.VolumePosture,
    val pvInterval: LandmarkConfidenceInterval,
    val confidenceTier: LandmarkConfidenceTier,
    val personalizedFromLogs: Boolean,
)

data class EffectiveLiftHighlight(
    val liftId: LiftId,
    val liftName: String,
    val muscle: MuscleGroup,
    val evidence: EvidenceLevel,
    val effectivenessScore: Double,
    val effectivenessTier: UserEffectivenessTier,
    val confidenceTier: LandmarkConfidenceTier,
)

data class VolumeResponseHighlight(
    val muscle: MuscleGroup,
    val evidence: EvidenceLevel,
    /** True when higher weekly volume correlated with strength gains for this muscle. */
    val respondsToVolume: Boolean,
)

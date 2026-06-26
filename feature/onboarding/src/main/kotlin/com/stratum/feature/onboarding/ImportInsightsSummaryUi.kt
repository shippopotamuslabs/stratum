package com.stratum.feature.onboarding

import com.stratum.domain.importing.LandmarkConfidenceTier
import com.stratum.domain.importing.UserEffectivenessTier
import com.stratum.domain.response.EvidenceLevel

/**
 * Presentation snapshot of insights extracted from a completed historical import.
 */
data class ImportInsightsSummaryUi(
    val totalSessions: Int,
    val totalSets: Int,
    val analysisWeeks: Int,
    val enabledLiftCount: Int,
    val provenLiftCount: Int,
    val customLiftsCreated: Int,
    val liftsWithInsights: Int,
    val supersetPairs: Int,
    val volumeLandmarks: List<MuscleVolumeLandmarkUi>,
    val effectiveLifts: List<EffectiveLiftHighlightUi>,
    val volumeResponses: List<VolumeResponseHighlightUi>,
) {
    val landmarkCount: Int get() = volumeLandmarks.size
    val hasVolumeLandmarks: Boolean get() = volumeLandmarks.isNotEmpty()
    val hasEffectiveLifts: Boolean get() = effectiveLifts.isNotEmpty()
    val hasVolumeResponses: Boolean get() = volumeResponses.isNotEmpty()
}

data class MuscleVolumeLandmarkUi(
    val muscleLabel: String,
    val mevWeeklyHardSets: String,
    val mrvWeeklyHardSets: String,
    val confidenceTier: LandmarkConfidenceTier,
)

data class EffectiveLiftHighlightUi(
    val muscleLabel: String,
    val liftName: String,
    val effectivenessTier: UserEffectivenessTier,
    val confidenceTier: LandmarkConfidenceTier,
)

data class VolumeResponseHighlightUi(
    val muscleLabel: String,
    val evidence: EvidenceLevel,
    val respondsToVolume: Boolean,
)

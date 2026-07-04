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
    val volumeSnapshots: List<MuscleVolumeSnapshotUi>,
    val effectiveLifts: List<EffectiveLiftHighlightUi>,
    val volumeResponses: List<VolumeResponseHighlightUi>,
) {
    val snapshotCount: Int get() = volumeSnapshots.size
    val hasVolumeSnapshots: Boolean get() = volumeSnapshots.isNotEmpty()
    val hasEffectiveLifts: Boolean get() = effectiveLifts.isNotEmpty()
    val hasVolumeResponses: Boolean get() = volumeResponses.isNotEmpty()
    val usesMostlyPopulationDefaults: Boolean
        get() =
            volumeSnapshots.isNotEmpty() &&
                volumeSnapshots.count { !it.personalizedFromLogs } * 4 >= volumeSnapshots.size * 3
}

data class MuscleVolumeSnapshotUi(
    val muscleLabel: String,
    val mvWeeklyHardSets: String,
    val pvWeeklyHardSets: String,
    val rcWeeklyHardSets: String,
    val isSaturatedPosture: Boolean,
    val personalizedFromLogs: Boolean,
    val observedMinWeeklyHardSets: String,
    val observedMedianWeeklyHardSets: Double,
    val observedMedianWeeklyHardSetsLabel: String,
    val observedMaxWeeklyHardSets: String,
    val pvIntervalLabel: String,
    val confidenceTier: LandmarkConfidenceTier,
) {
    val hasPvInterval: Boolean get() = pvIntervalLabel.isNotEmpty()
}

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

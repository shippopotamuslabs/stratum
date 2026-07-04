package com.stratum.feature.onboarding

import com.stratum.domain.importing.EffectiveLiftHighlight
import com.stratum.domain.importing.HistoricalImportSummary
import com.stratum.domain.importing.IntervalMethod
import com.stratum.domain.importing.LandmarkConfidenceInterval
import com.stratum.domain.importing.LandmarkConfidenceTier
import com.stratum.domain.importing.MuscleVolumeHighlight
import com.stratum.domain.importing.UserEffectivenessTier
import com.stratum.domain.importing.VolumeResponseHighlight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.EvidenceLevel
import com.stratum.domain.response.modeling.VolumePosture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ImportInsightsSummaryMappingTest {
    @Test
    @DisplayName("Given domain summary when mapped then formats weekly sets and labels")
    fun mapsDomainSummaryToUi() {
        val summary =
            HistoricalImportSummary(
                totalSessions = 10,
                totalSets = 200,
                analysisWeeks = 8,
                enabledLiftCount = 24,
                provenLiftCount = 8,
                customLiftsCreated = 2,
                liftsWithInsights = 8,
                supersetPairs = 3,
                volumeHighlights =
                    listOf(
                        MuscleVolumeHighlight(
                            muscle = MuscleGroup.Chest,
                            retentionFloorMv = 10.0,
                            paretoVolumePv = 13.0,
                            recoverabilityCeilingRc = 18.0,
                            observedDoseMin = 8.0,
                            observedDoseMax = 14.0,
                            observedDoseMedian = 11.0,
                            posture = VolumePosture.Holding,
                            pvInterval = LandmarkConfidenceInterval(12.0, 14.0, IntervalMethod.EpochBlockedBootstrap),
                            confidenceTier = LandmarkConfidenceTier.Medium,
                            personalizedFromLogs = true,
                        ),
                    ),
                effectiveLifts =
                    listOf(
                        EffectiveLiftHighlight(
                            liftId = LiftId("bench-press"),
                            liftName = "Bench Press",
                            muscle = MuscleGroup.Chest,
                            evidence = EvidenceLevel.Proven,
                            effectivenessScore = 1.5,
                            effectivenessTier = UserEffectivenessTier.VeryEffective,
                            confidenceTier = LandmarkConfidenceTier.High,
                        ),
                    ),
                volumeResponses =
                    listOf(
                        VolumeResponseHighlight(
                            muscle = MuscleGroup.Chest,
                            evidence = EvidenceLevel.Hypothesis,
                            respondsToVolume = true,
                        ),
                    ),
            )

        val ui = summary.toImportInsightsSummaryUi()

        assertEquals("Chest", ui.volumeSnapshots.first().muscleLabel)
        assertEquals("10", ui.volumeSnapshots.first().mvWeeklyHardSets)
        assertEquals("13", ui.volumeSnapshots.first().pvWeeklyHardSets)
        assertEquals("18", ui.volumeSnapshots.first().rcWeeklyHardSets)
        assertEquals(false, ui.volumeSnapshots.first().isSaturatedPosture)
        assertEquals(true, ui.volumeSnapshots.first().personalizedFromLogs)
        assertEquals("8", ui.volumeSnapshots.first().observedMinWeeklyHardSets)
        assertEquals("11", ui.volumeSnapshots.first().observedMedianWeeklyHardSetsLabel)
        assertEquals(11.0, ui.volumeSnapshots.first().observedMedianWeeklyHardSets)
        assertEquals("14", ui.volumeSnapshots.first().observedMaxWeeklyHardSets)
        assertEquals("12–14", ui.volumeSnapshots.first().pvIntervalLabel)
        assertEquals(true, ui.volumeSnapshots.first().hasPvInterval)
        assertEquals("Chest", ui.effectiveLifts.first().muscleLabel)
        assertEquals("Bench Press", ui.effectiveLifts.first().liftName)
        assertEquals(UserEffectivenessTier.VeryEffective, ui.effectiveLifts.first().effectivenessTier)
        assertEquals(LandmarkConfidenceTier.High, ui.effectiveLifts.first().confidenceTier)
    }
}

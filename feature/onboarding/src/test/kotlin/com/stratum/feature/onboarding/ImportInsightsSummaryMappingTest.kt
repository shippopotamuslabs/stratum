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
                            estimatedMev = 10.0,
                            estimatedMav = 15.0,
                            estimatedMrv = 18.0,
                            estimatedPv = 13.0,
                            mevInterval = LandmarkConfidenceInterval(8.0, 12.0, IntervalMethod.DeltaMethod),
                            mavInterval = LandmarkConfidenceInterval(13.0, 17.0, IntervalMethod.DeltaMethod),
                            mrvInterval = LandmarkConfidenceInterval(16.0, 21.0, IntervalMethod.ResidualBootstrap),
                            pvInterval = LandmarkConfidenceInterval.Unavailable,
                            confidenceTier = LandmarkConfidenceTier.Medium,
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

        assertEquals("Chest", ui.volumeLandmarks.first().muscleLabel)
        assertEquals("10", ui.volumeLandmarks.first().mevWeeklyHardSets)
        assertEquals("15", ui.volumeLandmarks.first().mavWeeklyHardSets)
        assertEquals("18", ui.volumeLandmarks.first().mrvWeeklyHardSets)
        assertEquals("13", ui.volumeLandmarks.first().pvWeeklyHardSets)
        assertEquals("8–12", ui.volumeLandmarks.first().mevIntervalLabel)
        assertEquals("13–17", ui.volumeLandmarks.first().mavIntervalLabel)
        assertEquals("16–21", ui.volumeLandmarks.first().mrvIntervalLabel)
        assertEquals("", ui.volumeLandmarks.first().pvIntervalLabel)
        assertEquals("Chest", ui.effectiveLifts.first().muscleLabel)
        assertEquals("Bench Press", ui.effectiveLifts.first().liftName)
        assertEquals(UserEffectivenessTier.VeryEffective, ui.effectiveLifts.first().effectivenessTier)
        assertEquals(LandmarkConfidenceTier.High, ui.effectiveLifts.first().confidenceTier)
    }
}

package com.stratum.feature.onboarding

import com.stratum.domain.importing.HistoricalImportSummary
import com.stratum.domain.importing.LandmarkConfidenceTier
import com.stratum.domain.importing.MuscleVolumeHighlight
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.modeling.VolumePosture
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImportInsightsSummaryUiTest {
    @Test
    fun `usesMostlyPopulationDefaults when three quarters are prior only`() {
        val summary =
            HistoricalImportSummary(
                totalSessions = 1,
                totalSets = 1,
                analysisWeeks = 1,
                enabledLiftCount = 1,
                provenLiftCount = 1,
                customLiftsCreated = 0,
                liftsWithInsights = 1,
                supersetPairs = 0,
                volumeHighlights =
                    listOf(
                        highlight(MuscleGroup.Chest, personalized = false),
                        highlight(MuscleGroup.Back, personalized = false),
                        highlight(MuscleGroup.Quad, personalized = false),
                        highlight(MuscleGroup.Bicep, personalized = true),
                    ),
                effectiveLifts = emptyList(),
                volumeResponses = emptyList(),
            )

        assertTrue(summary.toImportInsightsSummaryUi().usesMostlyPopulationDefaults)
    }

    @Test
    fun `does not flag mostly defaults when half are personalized`() {
        val summary =
            HistoricalImportSummary(
                totalSessions = 1,
                totalSets = 1,
                analysisWeeks = 1,
                enabledLiftCount = 1,
                provenLiftCount = 1,
                customLiftsCreated = 0,
                liftsWithInsights = 1,
                supersetPairs = 0,
                volumeHighlights =
                    listOf(
                        highlight(MuscleGroup.Chest, personalized = true),
                        highlight(MuscleGroup.Back, personalized = false),
                    ),
                effectiveLifts = emptyList(),
                volumeResponses = emptyList(),
            )

        assertFalse(summary.toImportInsightsSummaryUi().usesMostlyPopulationDefaults)
    }

    private fun highlight(muscle: MuscleGroup, personalized: Boolean): MuscleVolumeHighlight =
        MuscleVolumeHighlight(
            muscle = muscle,
            retentionFloorMv = 4.0,
            paretoVolumePv = 13.0,
            recoverabilityCeilingRc = 20.0,
            observedDoseMin = 2.0,
            observedDoseMax = 8.0,
            observedDoseMedian = 5.0,
            posture = VolumePosture.Unclear,
            pvInterval = com.stratum.domain.importing.LandmarkConfidenceInterval.Unavailable,
            confidenceTier = LandmarkConfidenceTier.Low,
            personalizedFromLogs = personalized,
        )
}

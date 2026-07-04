package com.stratum.feature.onboarding

import com.stratum.domain.importing.LandmarkConfidenceTier
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.onboarding.SpecializationPreference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpecializationDraftMappingTest {
    @Test
    fun `saturated import muscle selected for specialize surfaces warning`() {
        val preference =
            SpecializationPreference(
                levels = mapOf(MuscleGroup.Chest to SpecializationLevel.Specialize),
            )
        val insights =
            ImportInsightsSummaryUi(
                totalSessions = 1,
                totalSets = 1,
                analysisWeeks = 1,
                enabledLiftCount = 1,
                provenLiftCount = 1,
                customLiftsCreated = 0,
                liftsWithInsights = 1,
                supersetPairs = 0,
                volumeSnapshots =
                    listOf(
                        MuscleVolumeSnapshotUi(
                            muscleLabel = "Chest",
                            mvWeeklyHardSets = "4",
                            pvWeeklyHardSets = "13",
                            rcWeeklyHardSets = "20",
                            isSaturatedPosture = true,
                            personalizedFromLogs = false,
                            observedMinWeeklyHardSets = "16",
                            observedMedianWeeklyHardSets = 18.0,
                            observedMedianWeeklyHardSetsLabel = "18",
                            observedMaxWeeklyHardSets = "20",
                            pvIntervalLabel = "11–15",
                            confidenceTier = LandmarkConfidenceTier.Medium,
                        ),
                    ),
                effectiveLifts = emptyList(),
                volumeResponses = emptyList(),
            )

        val draft = preference.toSpecializationDraft(insights)

        assertEquals(listOf("Chest"), draft.saturatedSpecializeWarningLabels)
    }

    @Test
    fun `non saturated muscle does not surface warning`() {
        val preference =
            SpecializationPreference(
                levels = mapOf(MuscleGroup.Back to SpecializationLevel.Specialize),
            )
        val insights =
            ImportInsightsSummaryUi(
                totalSessions = 1,
                totalSets = 1,
                analysisWeeks = 1,
                enabledLiftCount = 1,
                provenLiftCount = 1,
                customLiftsCreated = 0,
                liftsWithInsights = 1,
                supersetPairs = 0,
                volumeSnapshots =
                    listOf(
                        MuscleVolumeSnapshotUi(
                            muscleLabel = "Back",
                            mvWeeklyHardSets = "4",
                            pvWeeklyHardSets = "13",
                            rcWeeklyHardSets = "20",
                            isSaturatedPosture = false,
                            personalizedFromLogs = true,
                            observedMinWeeklyHardSets = "6",
                            observedMedianWeeklyHardSets = 8.0,
                            observedMedianWeeklyHardSetsLabel = "8",
                            observedMaxWeeklyHardSets = "10",
                            pvIntervalLabel = "",
                            confidenceTier = LandmarkConfidenceTier.Low,
                        ),
                    ),
                effectiveLifts = emptyList(),
                volumeResponses = emptyList(),
            )

        val draft = preference.toSpecializationDraft(insights)

        assertTrue(draft.saturatedSpecializeWarningLabels.isEmpty())
    }
}

package com.stratum.feature.onboarding

import com.stratum.domain.importing.LandmarkConfidenceTier
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.onboarding.SpecializationPreference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class MinimumVolumeDraftMappingTest {

    @Test
    @DisplayName("suggestMinimumVolumeMuscleLabels includes low-MV muscles from import")
    fun suggestsLowMvMusclesFromImport() {
        val insights = sampleImportInsights(
            muscleLabel = MuscleGroup.Calf.displayLabel,
            mvWeeklyHardSets = "4",
            observedMedianWeeklyHardSets = 3.0,
        )
        val suggestions = suggestMinimumVolumeMuscleLabels(
            importInsights = insights,
            preference = SpecializationPreference.Empty,
        )
        assertEquals(setOf(MuscleGroup.Calf.displayLabel), suggestions)
    }

    @Test
    @DisplayName("suggestMinimumVolumeMuscleLabels excludes specialized muscles")
    fun excludesSpecializedMuscles() {
        val insights = sampleImportInsights(
            muscleLabel = MuscleGroup.Calf.displayLabel,
            mvWeeklyHardSets = "4",
            observedMedianWeeklyHardSets = 3.0,
        )
        val preference = SpecializationPreference(
            levels = mapOf(MuscleGroup.Calf to SpecializationLevel.Specialize),
        )
        val suggestions = suggestMinimumVolumeMuscleLabels(
            importInsights = insights,
            preference = preference,
        )
        assertTrue(suggestions.isEmpty())
    }

    @Test
    @DisplayName("suggestMinimumVolumeMuscleLabels skips high-MV muscles")
    fun skipsHighMvMuscles() {
        val insights = sampleImportInsights(
            muscleLabel = MuscleGroup.Back.displayLabel,
            mvWeeklyHardSets = "10",
            observedMedianWeeklyHardSets = 12.0,
        )
        val suggestions = suggestMinimumVolumeMuscleLabels(
            importInsights = insights,
            preference = SpecializationPreference.Empty,
        )
        assertTrue(suggestions.isEmpty())
    }

    private fun sampleImportInsights(
        muscleLabel: String,
        mvWeeklyHardSets: String,
        observedMedianWeeklyHardSets: Double,
    ): ImportInsightsSummaryUi =
        ImportInsightsSummaryUi(
            totalSessions = 12,
            totalSets = 240,
            analysisWeeks = 8,
            enabledLiftCount = 20,
            provenLiftCount = 6,
            customLiftsCreated = 0,
            liftsWithInsights = 6,
            supersetPairs = 0,
            volumeSnapshots = listOf(
                MuscleVolumeSnapshotUi(
                    muscleLabel = muscleLabel,
                    mvWeeklyHardSets = mvWeeklyHardSets,
                    pvWeeklyHardSets = "12",
                    rcWeeklyHardSets = "18",
                    isSaturatedPosture = false,
                    personalizedFromLogs = false,
                    observedMinWeeklyHardSets = mvWeeklyHardSets,
                    observedMedianWeeklyHardSets = observedMedianWeeklyHardSets,
                    observedMedianWeeklyHardSetsLabel = observedMedianWeeklyHardSets.toInt().toString(),
                    observedMaxWeeklyHardSets = mvWeeklyHardSets,
                    pvIntervalLabel = "",
                    confidenceTier = LandmarkConfidenceTier.Medium,
                ),
            ),
            effectiveLifts = emptyList(),
            volumeResponses = emptyList(),
        )
}

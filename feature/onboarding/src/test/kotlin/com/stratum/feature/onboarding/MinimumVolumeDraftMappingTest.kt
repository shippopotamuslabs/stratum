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
    @DisplayName("suggestMinimumVolumeMuscleLabels includes low-MEV muscles from import")
    fun suggestsLowMevMusclesFromImport() {
        val insights = sampleImportInsights(
            muscleLabel = MuscleGroup.Calf.displayLabel,
            mevWeeklyHardSets = "4 sets",
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
            mevWeeklyHardSets = "4 sets",
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
    @DisplayName("suggestMinimumVolumeMuscleLabels skips high-MEV muscles")
    fun skipsHighMevMuscles() {
        val insights = sampleImportInsights(
            muscleLabel = MuscleGroup.Back.displayLabel,
            mevWeeklyHardSets = "10 sets",
        )
        val suggestions = suggestMinimumVolumeMuscleLabels(
            importInsights = insights,
            preference = SpecializationPreference.Empty,
        )
        assertTrue(suggestions.isEmpty())
    }

    private fun sampleImportInsights(
        muscleLabel: String,
        mevWeeklyHardSets: String,
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
            volumeLandmarks = listOf(
                MuscleVolumeLandmarkUi(
                    muscleLabel = muscleLabel,
                    mevWeeklyHardSets = mevWeeklyHardSets,
                    mavWeeklyHardSets = "12 sets",
                    mrvWeeklyHardSets = "18 sets",
                    pvWeeklyHardSets = "8 sets",
                    mevIntervalLabel = "",
                    mavIntervalLabel = "",
                    mrvIntervalLabel = "",
                    pvIntervalLabel = "",
                    confidenceTier = LandmarkConfidenceTier.Medium,
                ),
            ),
            effectiveLifts = emptyList(),
            volumeResponses = emptyList(),
        )
}

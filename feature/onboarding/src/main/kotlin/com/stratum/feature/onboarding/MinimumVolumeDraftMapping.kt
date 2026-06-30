package com.stratum.feature.onboarding

import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationPreference

internal fun SpecializationPreference.toMinimumVolumeDraft(
    importInsights: ImportInsightsSummaryUi? = null,
): MinimumVolumeDraftUi =
    MinimumVolumeDraftUi(
        selectedMuscleLabels = minimumVolumeMuscles.map { it.displayLabel }.toSet(),
        excludedMuscleLabels = specializedMuscles.map { it.displayLabel }.toSet(),
        suggestedMuscleLabels = suggestMinimumVolumeMuscleLabels(importInsights, this),
    )

/**
 * Suggests MV candidates from import landmarks when median weekly volume is below MEV.
 */
internal fun suggestMinimumVolumeMuscleLabels(
    importInsights: ImportInsightsSummaryUi?,
    preference: SpecializationPreference,
): Set<String> {
    if (importInsights == null || !importInsights.hasVolumeLandmarks) return emptySet()
    val specialized = preference.specializedMuscles
    return importInsights.volumeLandmarks.mapNotNull { landmark ->
        val muscle = muscleGroupFromLabel(landmark.muscleLabel) ?: return@mapNotNull null
        if (muscle in specialized || muscle in preference.minimumVolumeMuscles) return@mapNotNull null
        val mev = parseWeeklySetsLabel(landmark.mevWeeklyHardSets) ?: return@mapNotNull null
        if (mev <= 6.0) muscle.displayLabel else null
    }.toSet()
}

private fun parseWeeklySetsLabel(label: String): Double? {
    val token = label.trim().split(Regex("[^0-9.]+")).firstOrNull { it.isNotEmpty() } ?: return null
    return token.toDoubleOrNull()
}

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
 * Suggests MV candidates when logged median weekly volume is below the retention floor (MV).
 */
internal fun suggestMinimumVolumeMuscleLabels(
    importInsights: ImportInsightsSummaryUi?,
    preference: SpecializationPreference,
): Set<String> {
    if (importInsights == null || !importInsights.hasVolumeSnapshots) return emptySet()
    val specialized = preference.specializedMuscles
    return importInsights.volumeSnapshots.mapNotNull { snapshot ->
        val muscle = muscleGroupFromLabel(snapshot.muscleLabel) ?: return@mapNotNull null
        if (muscle in specialized || muscle in preference.minimumVolumeMuscles) return@mapNotNull null
        val mv = parseWeeklySetsLabel(snapshot.mvWeeklyHardSets) ?: return@mapNotNull null
        if (snapshot.observedMedianWeeklyHardSets < mv) muscle.displayLabel else null
    }.toSet()
}

private fun parseWeeklySetsLabel(label: String): Double? {
    val token = label.trim().split(Regex("[^0-9.]+")).firstOrNull { it.isNotEmpty() } ?: return null
    return token.toDoubleOrNull()
}

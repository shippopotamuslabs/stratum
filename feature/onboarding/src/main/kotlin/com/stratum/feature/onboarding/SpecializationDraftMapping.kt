package com.stratum.feature.onboarding

import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.response.modeling.VolumePosture

/**
 * Maps the domain [SpecializationPreference] to the UI-shaped [SpecializationDraftUi] and
 * back. Composables only ever see [SpecializationDraftUi] (ARCHITECTURE.md §4.3).
 */
internal fun SpecializationPreference.toSpecializationDraft(
    importInsights: ImportInsightsSummaryUi? = null,
): SpecializationDraftUi {
    val selected = specializedMuscles.map { it.displayLabel }.toSet()
    return SpecializationDraftUi(
        selectedMuscleLabels = selected,
        saturatedSpecializeWarningLabels = saturatedSpecializeWarningLabels(importInsights, selected),
    )
}

internal fun muscleGroupFromLabel(label: String): MuscleGroup? =
    MuscleGroup.entries.firstOrNull { it.displayLabel == label }

/**
 * Muscles the user selected to specialize that import marked as already saturated on volume.
 */
internal fun saturatedSpecializeWarningLabels(
    importInsights: ImportInsightsSummaryUi?,
    selectedMuscleLabels: Set<String>,
): List<String> {
    if (importInsights == null || selectedMuscleLabels.isEmpty()) return emptyList()
    val saturatedLabels =
        importInsights.volumeSnapshots
            .filter { it.isSaturatedPosture }
            .map { it.muscleLabel }
            .toSet()
    return selectedMuscleLabels.filter { it in saturatedLabels }.sorted()
}

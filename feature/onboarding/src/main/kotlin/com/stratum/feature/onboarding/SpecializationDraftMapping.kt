package com.stratum.feature.onboarding

import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationPreference

/**
 * Maps the domain [SpecializationPreference] to the UI-shaped [SpecializationDraftUi] and
 * back. Composables only ever see [SpecializationDraftUi] (ARCHITECTURE.md §4.3).
 */
internal fun SpecializationPreference.toSpecializationDraft(): SpecializationDraftUi =
    SpecializationDraftUi(
        selectedMuscleLabels = specializedMuscles.map { it.displayLabel }.toSet(),
    )

internal fun muscleGroupFromLabel(label: String): MuscleGroup? =
    MuscleGroup.entries.firstOrNull { it.displayLabel == label }

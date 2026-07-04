package com.stratum.core.ui.component

import com.stratum.domain.onboarding.MuscleGroup

/**
 * Presentation input for [StratumMuscleFilterCard]. Carries the [muscle] so the swap sheet's
 * filter intent can address it without the feature layer needing to map display labels back to
 * [MuscleGroup]s. Mirrors [StratumMuscleVolumeCardState] minus the volume counts.
 */
data class StratumMuscleFilterCardState(
    val muscle: MuscleGroup,
    val displayName: String,
)

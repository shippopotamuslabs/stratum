package com.stratum.core.ui.component

import com.stratum.domain.onboarding.MuscleGroup

/** Presentation input for [StratumMuscleVolumeCard]. */
data class StratumMuscleVolumeCardState(
    val muscle: MuscleGroup,
    val displayName: String,
    val exerciseCount: Int,
    val setCount: Double,
)

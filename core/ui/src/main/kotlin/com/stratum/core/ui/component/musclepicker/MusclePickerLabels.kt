package com.stratum.core.ui.component.musclepicker

import com.stratum.domain.onboarding.MuscleGroup

/**
 * Display labels for the three deltoid heads shown in the sub-picker. Uses [MuscleGroup.displayLabel]
 * from domain so emitted toggle labels round-trip with the ViewModel string contract.
 */
internal val DELTOID_LABELS: List<Pair<MuscleGroup, String>> = listOf(
    MuscleGroup.AnteriorDeltoid to MuscleGroup.AnteriorDeltoid.displayLabel,
    MuscleGroup.LateralDeltoid to MuscleGroup.LateralDeltoid.displayLabel,
    MuscleGroup.PosteriorDeltoid to MuscleGroup.PosteriorDeltoid.displayLabel,
)

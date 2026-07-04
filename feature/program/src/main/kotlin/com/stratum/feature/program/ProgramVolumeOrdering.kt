package com.stratum.feature.program

import com.stratum.core.ui.component.StratumMuscleVolumeCardState
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.MuscleVolumeSummary
import com.stratum.core.ui.component.toVolumeDisplayName

internal val upperVolumeMuscleOrder: List<MuscleGroup> =
    listOf(
        MuscleGroup.Chest,
        MuscleGroup.Back,
        MuscleGroup.Tricep,
        MuscleGroup.Bicep,
        MuscleGroup.LateralDeltoid,
        MuscleGroup.PosteriorDeltoid,
        MuscleGroup.AnteriorDeltoid,
        MuscleGroup.Trap,
        MuscleGroup.Forearm,
        MuscleGroup.Ab,
    )

internal val lowerVolumeMuscleOrder: List<MuscleGroup> =
    listOf(
        MuscleGroup.Quad,
        MuscleGroup.Hamstring,
        MuscleGroup.Glute,
        MuscleGroup.Calf,
        MuscleGroup.LowerBack,
        MuscleGroup.Abductor,
        MuscleGroup.Adductor,
    )

internal fun List<MuscleVolumeSummary>.toUpperVolumeCards(): List<StratumMuscleVolumeCardState> =
    toOrderedVolumeCards(order = upperVolumeMuscleOrder)

internal fun List<MuscleVolumeSummary>.toLowerVolumeCards(): List<StratumMuscleVolumeCardState> =
    toOrderedVolumeCards(order = lowerVolumeMuscleOrder)

private fun List<MuscleVolumeSummary>.toOrderedVolumeCards(
    order: List<MuscleGroup>,
): List<StratumMuscleVolumeCardState> {
    val summariesByMuscle = associateBy { summary -> summary.muscle }
    return order.mapNotNull { muscle -> summariesByMuscle[muscle]?.toCardState() }
}

private fun MuscleVolumeSummary.toCardState(): StratumMuscleVolumeCardState =
    StratumMuscleVolumeCardState(
        muscle = muscle,
        displayName = muscle.toVolumeDisplayName(),
        exerciseCount = exerciseCount,
        setCount = setCount,
    )

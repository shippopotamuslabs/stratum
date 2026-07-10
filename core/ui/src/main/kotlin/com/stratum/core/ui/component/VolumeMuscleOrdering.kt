package com.stratum.core.ui.component

import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.MuscleVolumeSummary

/** Fixed upper-body muscle order for volume carousels (Programs, Edit Workout, History). */
val upperVolumeMuscleOrder: List<MuscleGroup> =
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

/** Fixed lower-body muscle order for volume carousels. */
val lowerVolumeMuscleOrder: List<MuscleGroup> =
    listOf(
        MuscleGroup.Quad,
        MuscleGroup.Hamstring,
        MuscleGroup.Glute,
        MuscleGroup.Calf,
        MuscleGroup.LowerBack,
        MuscleGroup.Abductor,
        MuscleGroup.Adductor,
    )

fun List<MuscleVolumeSummary>.toUpperVolumeCards(): List<StratumMuscleVolumeCardState> =
    toOrderedVolumeCards(order = upperVolumeMuscleOrder)

fun List<MuscleVolumeSummary>.toLowerVolumeCards(): List<StratumMuscleVolumeCardState> =
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

package com.stratum.core.ui.component

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup

fun MuscleGroup.toVolumeDisplayName(): String =
    when (this) {
        MuscleGroup.Chest -> "Chest"
        MuscleGroup.Back -> "Back"
        MuscleGroup.Quad -> "Quads"
        MuscleGroup.Hamstring -> "Hamstrings"
        MuscleGroup.Glute -> "Glutes"
        MuscleGroup.PosteriorDeltoid -> "Rear Delts"
        MuscleGroup.LateralDeltoid -> "Lateral Delts"
        MuscleGroup.AnteriorDeltoid -> "Front Delts"
        MuscleGroup.Tricep -> "Triceps"
        MuscleGroup.Bicep -> "Biceps"
        MuscleGroup.Trap -> "Traps"
        MuscleGroup.Calf -> "Calves"
        MuscleGroup.Forearm -> "Forearms"
        MuscleGroup.Ab -> "Abs"
        MuscleGroup.LowerBack -> "Lower Back"
        MuscleGroup.Abductor -> "Abductors"
        MuscleGroup.Adductor -> "Adductors"
    }

fun MovementPattern.toProgramDisplayName(): String =
    when (this) {
        MovementPattern.PlantarFlexion -> "Calves"
        else -> displayName
    }

package com.stratum.feature.program

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup

/**
 * User-facing display names for program-screen presentation. Keeps domain identifiers
 * (e.g. [MuscleGroup.Calf] → "PlantarFlexion" in domain) separate from gym-friendly labels.
 */
internal fun MuscleGroup.toVolumeDisplayName(): String =
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

/** Program-screen label for a lift movement pattern (may differ from [MovementPattern.displayName]). */
internal fun MovementPattern.toProgramDisplayName(): String =
    when (this) {
        MovementPattern.PlantarFlexion -> "Calves"
        else -> displayName
    }

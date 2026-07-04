package com.stratum.core.ui.component.musclepicker

import com.stratum.domain.onboarding.MuscleGroup

internal enum class BodyCrop {
    UpperBody,
    LowerBody,
}

internal fun bodyCropForMuscle(muscle: MuscleGroup): BodyCrop =
    when (muscle) {
        MuscleGroup.Quad,
        MuscleGroup.Hamstring,
        MuscleGroup.Glute,
        MuscleGroup.Calf,
        MuscleGroup.Abductor,
        MuscleGroup.Adductor,
        -> BodyCrop.LowerBody
        else -> BodyCrop.UpperBody
    }

internal fun bodyTargetCropTier(target: BodyTarget): BodyCrop =
    when (target) {
        is BodyTarget.Single -> bodyCropForMuscle(target.muscle)
        is BodyTarget.DeltoidRegion -> BodyCrop.UpperBody
    }

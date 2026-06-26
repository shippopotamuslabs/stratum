package com.stratum.domain.importing

import com.stratum.domain.onboarding.MuscleGroup

/**
 * Literature-informed weekly hard-set bands (primary-muscle counting) used to sanity-check
 * observational MEV/MRV estimates. Values are approximate population ranges — personal
 * estimates outside these bands lower confidence rather than being rejected outright.
 */
data class VolumeLandmarkPrior(
    val mevLow: Double,
    val mevHigh: Double,
    val mrvLow: Double,
    val mrvHigh: Double,
) {
    val mevMid: Double get() = (mevLow + mevHigh) / 2.0
    val mrvMid: Double get() = (mrvLow + mrvHigh) / 2.0
}

object VolumeLandmarkPriors {
    fun forMuscle(muscle: MuscleGroup): VolumeLandmarkPrior =
        when (muscle) {
            MuscleGroup.Chest ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_LARGE,
                    VolumeLandmarkPriorBands.MEV_HIGH_LARGE,
                    VolumeLandmarkPriorBands.MRV_LOW_MAJOR,
                    VolumeLandmarkPriorBands.MRV_HIGH_MAJOR,
                )
            MuscleGroup.Back ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_LARGE,
                    VolumeLandmarkPriorBands.MEV_HIGH_EXTENDED,
                    VolumeLandmarkPriorBands.MRV_LOW_EXTENDED,
                    VolumeLandmarkPriorBands.MRV_HIGH_QUAD,
                )
            MuscleGroup.Quad ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_LARGE,
                    VolumeLandmarkPriorBands.MEV_HIGH_EXTENDED,
                    VolumeLandmarkPriorBands.MRV_LOW_EXTENDED,
                    VolumeLandmarkPriorBands.MRV_HIGH_QUAD,
                )
            MuscleGroup.Hamstring ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_COMPACT,
                    VolumeLandmarkPriorBands.MEV_HIGH_COMPACT,
                    VolumeLandmarkPriorBands.MRV_LOW_LARGE,
                    VolumeLandmarkPriorBands.MRV_HIGH_LARGE,
                )
            MuscleGroup.Glute ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_COMPACT,
                    VolumeLandmarkPriorBands.MEV_HIGH_COMPACT,
                    VolumeLandmarkPriorBands.MRV_LOW_LARGE,
                    VolumeLandmarkPriorBands.MRV_HIGH_LARGE,
                )
            MuscleGroup.PosteriorDeltoid ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_COMPACT,
                    VolumeLandmarkPriorBands.MEV_HIGH_COMPACT,
                    VolumeLandmarkPriorBands.MRV_LOW_EXTENDED,
                    VolumeLandmarkPriorBands.MRV_HIGH_EXTENDED,
                )
            MuscleGroup.LateralDeltoid ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_COMPACT,
                    VolumeLandmarkPriorBands.MEV_HIGH_COMPACT,
                    VolumeLandmarkPriorBands.MRV_LOW_EXTENDED,
                    VolumeLandmarkPriorBands.MRV_HIGH_EXTENDED,
                )
            MuscleGroup.AnteriorDeltoid ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_COMPACT,
                    VolumeLandmarkPriorBands.MEV_HIGH_COMPACT,
                    VolumeLandmarkPriorBands.MRV_LOW_EXTENDED,
                    VolumeLandmarkPriorBands.MRV_HIGH_EXTENDED,
                )
            MuscleGroup.Tricep ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_COMPACT,
                    VolumeLandmarkPriorBands.MEV_HIGH_COMPACT,
                    VolumeLandmarkPriorBands.MRV_LOW_EXTENDED,
                    VolumeLandmarkPriorBands.MRV_HIGH_EXTENDED,
                )
            MuscleGroup.Bicep ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_COMPACT,
                    VolumeLandmarkPriorBands.MEV_HIGH_COMPACT,
                    VolumeLandmarkPriorBands.MRV_LOW_LARGE,
                    VolumeLandmarkPriorBands.MRV_HIGH_MAJOR,
                )
            MuscleGroup.Trap ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_COMPACT,
                    VolumeLandmarkPriorBands.MEV_HIGH_COMPACT,
                    VolumeLandmarkPriorBands.MRV_LOW_LARGE,
                    VolumeLandmarkPriorBands.MRV_HIGH_LARGE,
                )
            MuscleGroup.Calf ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_STANDARD,
                    VolumeLandmarkPriorBands.MEV_HIGH_STANDARD,
                    VolumeLandmarkPriorBands.MRV_LOW_CALF,
                    VolumeLandmarkPriorBands.MRV_HIGH_CALF,
                )
            MuscleGroup.Forearm ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_MINIMAL,
                    VolumeLandmarkPriorBands.MEV_HIGH_MINIMAL,
                    VolumeLandmarkPriorBands.MRV_LOW_COMPACT,
                    VolumeLandmarkPriorBands.MRV_HIGH_COMPACT,
                )
            MuscleGroup.Ab ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_NONE,
                    VolumeLandmarkPriorBands.MEV_HIGH_MINIMAL,
                    VolumeLandmarkPriorBands.MRV_LOW_STANDARD,
                    VolumeLandmarkPriorBands.MRV_HIGH_LARGE,
                )
            MuscleGroup.LowerBack ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_NONE,
                    VolumeLandmarkPriorBands.MEV_HIGH_MINIMAL,
                    VolumeLandmarkPriorBands.MRV_LOW_STANDARD,
                    VolumeLandmarkPriorBands.MRV_HIGH_STANDARD,
                )
            MuscleGroup.Abductor ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_MINIMAL,
                    VolumeLandmarkPriorBands.MEV_HIGH_MINIMAL,
                    VolumeLandmarkPriorBands.MRV_LOW_COMPACT,
                    VolumeLandmarkPriorBands.MRV_HIGH_COMPACT,
                )
            MuscleGroup.Adductor ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_MINIMAL,
                    VolumeLandmarkPriorBands.MEV_HIGH_MINIMAL,
                    VolumeLandmarkPriorBands.MRV_LOW_COMPACT,
                    VolumeLandmarkPriorBands.MRV_HIGH_COMPACT,
                )
        }
}

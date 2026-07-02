package com.stratum.domain.importing

import com.stratum.domain.onboarding.MuscleGroup

/**
 * Literature-informed weekly **fractional** hard-set bands used to sanity-check observational
 * MEV/MRV/MAV/PV estimates. Credits match program generation: 1.0 per primary muscle per set,
 * 0.5 per secondary ([com.stratum.domain.program.generation.context.PRIMARY_SET_CREDIT] /
 * [com.stratum.domain.program.generation.context.SECONDARY_SET_CREDIT]).
 *
 * MAV mid is (MEV-high + MRV-low) / 2. Literature [pvMid] is a maintenance dose prior at 85% of
 * MAV-mid — not a fitted Pareto point (that requires personal dose–response data).
 */
data class VolumeLandmarkPrior(
    val mevLow: Double,
    val mevHigh: Double,
    val mrvLow: Double,
    val mrvHigh: Double,
) {
    val mevMid: Double get() = (mevLow + mevHigh) / 2.0
    val mrvMid: Double get() = (mrvLow + mrvHigh) / 2.0
    val mavMid: Double get() = (mevHigh + mrvLow) / 2.0
    val pvMid: Double get() = literatureMaintenanceVolumeFromMav(mavMid, mvMid)
    /** Maintenance volume — bare minimum to retain muscle (below MEV productive range). */
    val mvMid: Double get() = kotlin.math.max(2.0, mevMid * 0.45)
}

object VolumeLandmarkPriors {
    fun forMuscle(muscle: MuscleGroup): VolumeLandmarkPrior =
        when (muscle) {
            // Fractional MEV 6, MAV 12–20, MRV 22+
            MuscleGroup.Chest ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_STANDARD,
                    VolumeLandmarkPriorBands.MEV_HIGH_LARGE,
                    VolumeLandmarkPriorBands.MRV_LOW_CHEST,
                    VolumeLandmarkPriorBands.MRV_HIGH_CHEST,
                )
            // Fractional MEV 8, MAV 14–22, MRV 25+
            MuscleGroup.Back ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_LARGE,
                    VolumeLandmarkPriorBands.MEV_HIGH_EXTENDED,
                    VolumeLandmarkPriorBands.MRV_LOW_BACK,
                    VolumeLandmarkPriorBands.MRV_HIGH_BACK,
                )
            // Fractional MEV 6, MAV 12–18, MRV 20+
            MuscleGroup.Quad ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_STANDARD,
                    VolumeLandmarkPriorBands.MEV_HIGH_LARGE,
                    VolumeLandmarkPriorBands.MRV_LOW_QUAD,
                    VolumeLandmarkPriorBands.MRV_HIGH_QUAD,
                )
            // Fractional MEV 4, MAV 10–16, MRV 18+
            MuscleGroup.Hamstring ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_COMPACT,
                    VolumeLandmarkPriorBands.MEV_HIGH_STANDARD,
                    VolumeLandmarkPriorBands.MRV_LOW_HAM,
                    VolumeLandmarkPriorBands.MRV_HIGH_HAM,
                )
            // Fractional MEV 6, MAV 12–18, MRV 22+
            MuscleGroup.Glute ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_STANDARD,
                    VolumeLandmarkPriorBands.MEV_HIGH_LARGE,
                    VolumeLandmarkPriorBands.MRV_LOW_GLUTE,
                    VolumeLandmarkPriorBands.MRV_HIGH_GLUTE,
                )
            // Fractional MEV 12, MAV 20–28, MRV 35+ (rear delts — heavy secondary credit)
            MuscleGroup.PosteriorDeltoid ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_FRAC_SECONDARY_HEAVY,
                    VolumeLandmarkPriorBands.MEV_HIGH_FRAC_REAR_DELT,
                    VolumeLandmarkPriorBands.MRV_LOW_FRAC_REAR_DELT,
                    VolumeLandmarkPriorBands.MRV_HIGH_FRAC_REAR_DELT,
                )
            // Fractional MEV 6, MAV 12–20, MRV 26+ (side delts)
            MuscleGroup.LateralDeltoid ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_STANDARD,
                    VolumeLandmarkPriorBands.MEV_HIGH_LARGE,
                    VolumeLandmarkPriorBands.MRV_LOW_SIDE_DELT,
                    VolumeLandmarkPriorBands.MRV_HIGH_SIDE_DELT,
                )
            // Fractional MEV 4, MAV 8–12, MRV 16+ (front delts)
            MuscleGroup.AnteriorDeltoid ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_COMPACT,
                    VolumeLandmarkPriorBands.MEV_HIGH_COMPACT,
                    VolumeLandmarkPriorBands.MRV_LOW_FRONT_DELT,
                    VolumeLandmarkPriorBands.MRV_HIGH_FRONT_DELT,
                )
            // Fractional MEV 12, MAV 18–26, MRV 30+ (includes press secondaries)
            MuscleGroup.Tricep ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_FRAC_SECONDARY_HEAVY,
                    VolumeLandmarkPriorBands.MEV_HIGH_FRAC_ARM,
                    VolumeLandmarkPriorBands.MRV_LOW_FRAC_ARM,
                    VolumeLandmarkPriorBands.MRV_HIGH_FRAC_ARM,
                )
            // Fractional MEV 12, MAV 18–26, MRV 30+ (includes pull secondaries)
            MuscleGroup.Bicep ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_FRAC_SECONDARY_HEAVY,
                    VolumeLandmarkPriorBands.MEV_HIGH_FRAC_ARM,
                    VolumeLandmarkPriorBands.MRV_LOW_FRAC_ARM,
                    VolumeLandmarkPriorBands.MRV_HIGH_FRAC_ARM,
                )
            MuscleGroup.Trap ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_COMPACT,
                    VolumeLandmarkPriorBands.MEV_HIGH_COMPACT,
                    VolumeLandmarkPriorBands.MRV_LOW_LARGE,
                    VolumeLandmarkPriorBands.MRV_HIGH_LARGE,
                )
            // Direct-dominant; fractional ≈ primary (no secondary table row)
            MuscleGroup.Calf ->
                VolumeLandmarkPrior(
                    VolumeLandmarkPriorBands.MEV_LOW_STANDARD,
                    VolumeLandmarkPriorBands.MEV_HIGH_LARGE,
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
                    VolumeLandmarkPriorBands.MRV_LOW_AB,
                    VolumeLandmarkPriorBands.MRV_HIGH_AB,
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

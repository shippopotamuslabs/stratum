package com.stratum.domain.importing

/**
 * Literature-informed weekly **fractional** hard-set bands (1.0 primary / 0.5 secondary per set).
 *
 * Population ranges match the fractional MEV / MAV / MRV table used by program generation
 * ([com.stratum.domain.program.generation.validation.computeWorkoutMuscleVolume] credits).
 * MAV mid is derived in [VolumeLandmarkPrior]. Literature PV is a maintenance dose prior at
 * [LITERATURE_MAINTENANCE_PV_MAV_FRACTION] × MAV-mid (see [literatureMaintenanceVolumeFromMav]).
 */
internal object VolumeLandmarkPriorBands {
    const val MEV_LOW_NONE = 0.0
    const val MEV_LOW_MINIMAL = 2.0
    const val MEV_LOW_COMPACT = 4.0
    const val MEV_LOW_STANDARD = 6.0
    const val MEV_LOW_LARGE = 8.0
    const val MEV_LOW_FRAC_SECONDARY_HEAVY = 12.0

    const val MEV_HIGH_MINIMAL = 4.0
    const val MEV_HIGH_COMPACT = 8.0
    const val MEV_HIGH_STANDARD = 10.0
    const val MEV_HIGH_LARGE = 12.0
    const val MEV_HIGH_EXTENDED = 14.0
    const val MEV_HIGH_FRAC_ARM = 18.0
    const val MEV_HIGH_FRAC_REAR_DELT = 20.0

    const val MRV_LOW_COMPACT = 8.0
    const val MRV_LOW_STANDARD = 10.0
    const val MRV_LOW_LARGE = 12.0
    const val MRV_LOW_EXTENDED = 14.0
    const val MRV_LOW_MAJOR = 16.0
    const val MRV_LOW_FRONT_DELT = 16.0
    const val MRV_LOW_HAM = 18.0
    const val MRV_LOW_QUAD = 20.0
    const val MRV_LOW_CALF = 20.0
    const val MRV_LOW_CHEST = 22.0
    const val MRV_LOW_GLUTE = 22.0
    const val MRV_LOW_BACK = 25.0
    const val MRV_LOW_SIDE_DELT = 26.0
    const val MRV_LOW_FRAC_ARM = 30.0
    const val MRV_LOW_FRAC_REAR_DELT = 35.0
    const val MRV_LOW_AB = 25.0

    const val MRV_HIGH_COMPACT = 16.0
    const val MRV_HIGH_STANDARD = 18.0
    const val MRV_HIGH_LARGE = 20.0
    const val MRV_HIGH_EXTENDED = 22.0
    const val MRV_HIGH_MAJOR = 24.0
    const val MRV_HIGH_FRONT_DELT = 20.0
    const val MRV_HIGH_HAM = 22.0
    const val MRV_HIGH_QUAD = 24.0
    const val MRV_HIGH_CALF = 24.0
    const val MRV_HIGH_CHEST = 26.0
    const val MRV_HIGH_GLUTE = 26.0
    const val MRV_HIGH_BACK = 28.0
    const val MRV_HIGH_SIDE_DELT = 30.0
    const val MRV_HIGH_FRAC_ARM = 34.0
    const val MRV_HIGH_FRAC_REAR_DELT = 40.0
    const val MRV_HIGH_AB = 28.0

    /** Literature maintenance PV prior = this fraction × MAV-mid (personal fits use [DoseResponseQuadraticFit]). */
    const val LITERATURE_MAINTENANCE_PV_MAV_FRACTION = 0.85
}

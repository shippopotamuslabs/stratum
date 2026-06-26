package com.stratum.domain.importing

/** Literature-informed weekly hard-set bands per muscle group (primary-muscle counting). */
internal object VolumeLandmarkPriorBands {
    const val MEV_LOW_NONE = 0.0
    const val MEV_LOW_MINIMAL = 2.0
    const val MEV_LOW_COMPACT = 4.0
    const val MEV_LOW_STANDARD = 6.0
    const val MEV_LOW_LARGE = 8.0

    const val MEV_HIGH_MINIMAL = 4.0
    const val MEV_HIGH_COMPACT = 8.0
    const val MEV_HIGH_STANDARD = 10.0
    const val MEV_HIGH_LARGE = 12.0
    const val MEV_HIGH_EXTENDED = 14.0

    const val MRV_LOW_COMPACT = 8.0
    const val MRV_LOW_STANDARD = 10.0
    const val MRV_LOW_LARGE = 12.0
    const val MRV_LOW_EXTENDED = 14.0
    const val MRV_LOW_MAJOR = 16.0
    const val MRV_LOW_CALF = 16.0

    const val MRV_HIGH_COMPACT = 16.0
    const val MRV_HIGH_STANDARD = 18.0
    const val MRV_HIGH_LARGE = 20.0
    const val MRV_HIGH_EXTENDED = 22.0
    const val MRV_HIGH_MAJOR = 24.0
    const val MRV_HIGH_QUAD = 26.0
    const val MRV_HIGH_CALF = 28.0
}

package com.stratum.domain.workout


/**
 * Shared cold-start shell for lifts / sets added mid-session (live overlay) and when
 * materializing those adds onto a workout template.
 */
object AddedLiftDefaults {
    const val REP_MIN: Int = 8
    const val REP_MAX: Int = 12
    const val RIR: Int = 2
    const val REST_SECONDS: Int = 120
}

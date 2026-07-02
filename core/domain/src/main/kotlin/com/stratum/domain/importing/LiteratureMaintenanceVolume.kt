@file:Suppress("MagicNumber")

package com.stratum.domain.importing

import kotlin.math.round

/**
 * Literature maintenance dose prior: [mavMid] × [VolumeLandmarkPriorBands.LITERATURE_MAINTENANCE_PV_MAV_FRACTION],
 * clamped to [mvMid, mavMid] and rounded to half-sets.
 *
 * Not a fitted Pareto point — true PV (80% of peak response) comes from [DoseResponseQuadraticFit] when
 * import history provides dose–progress pairs.
 */
internal fun literatureMaintenanceVolumeFromMav(mavMid: Double, mvMid: Double): Double {
    val raw = mavMid * VolumeLandmarkPriorBands.LITERATURE_MAINTENANCE_PV_MAV_FRACTION
    return (round(raw * 2) / 2.0).coerceIn(mvMid, mavMid)
}

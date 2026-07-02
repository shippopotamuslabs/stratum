@file:Suppress("MagicNumber")

package com.stratum.domain.importing

import com.stratum.domain.onboarding.TrainingAge
import kotlin.math.round

/**
 * Population-level weekly **fractional** set anchors (1.0 primary / 0.5 secondary per working set).
 *
 * Derived from Pelland et al. (2025) √ dose–response with [RECOVERABILITY_CEILING_RC] = 20 and
 * [PARETO_VOLUME_PV] = 0.8² × RC. Per-muscle tables are not evidence-backed; one global prior applies
 * to all muscles. Personal import data may nudge [paretoVolumePv] and [growthEntryFloor] per muscle.
 */
data class ResolvedGlobalVolumePriors(
    val retentionFloorMv: Double,
    val paretoVolumePv: Double,
    val recoverabilityCeilingRc: Double,
    val growthEntryFloor: Double,
    val maintenanceCeiling: Double,
)

object GlobalVolumePriors {
    const val RETENTION_FLOOR_MV = 4.0
    const val PARETO_VOLUME_PV = 13.0
    const val RECOVERABILITY_CEILING_RC = 20.0
    const val GROWTH_ENTRY_FLOOR = 6.0
    const val PARETO_BENEFIT_FRACTION = 0.8
    const val FRACTIONAL_SESSION_PUOS = 11.0

    /** Remmert et al. per-session PUOS; used for session-level fractional credit caps. */
    const val SESSION_FRACTIONAL_PUOS = FRACTIONAL_SESSION_PUOS

    fun paretoVolumeFromCeiling(rc: Double = RECOVERABILITY_CEILING_RC): Double =
        roundHalfSet(PARETO_BENEFIT_FRACTION * PARETO_BENEFIT_FRACTION * rc)

    fun maintenanceCeiling(
        pv: Double = PARETO_VOLUME_PV,
        rc: Double = RECOVERABILITY_CEILING_RC,
    ): Double = roundHalfSet((pv + rc) / 2.0)

    fun forTrainingAge(trainingAge: TrainingAge?): ResolvedGlobalVolumePriors {
        val mv =
            when (trainingAge) {
                TrainingAge.LateIntermediate -> 5.0
                TrainingAge.Advanced -> 6.0
                else -> RETENTION_FLOOR_MV
            }
        val pv = PARETO_VOLUME_PV
        val rc = RECOVERABILITY_CEILING_RC
        return ResolvedGlobalVolumePriors(
            retentionFloorMv = mv,
            paretoVolumePv = pv,
            recoverabilityCeilingRc = rc,
            growthEntryFloor = GROWTH_ENTRY_FLOOR,
            maintenanceCeiling = maintenanceCeiling(pv, rc),
        )
    }

    fun forTrainingAgeName(trainingAge: String?): ResolvedGlobalVolumePriors =
        forTrainingAge(trainingAge?.let { runCatching { TrainingAge.valueOf(it) }.getOrNull() })

    private fun roundHalfSet(value: Double): Double = round(value * 2) / 2.0
}

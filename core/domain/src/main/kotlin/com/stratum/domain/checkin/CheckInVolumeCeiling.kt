package com.stratum.domain.checkin

import com.stratum.domain.importing.GlobalVolumePriors
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.modeling.MuscleVolumeResponseSnapshot
import com.stratum.domain.response.modeling.VolumePosture

/**
 * Whether program weekly hard-set volume is high enough that opportunistic +1 set is a poor lever.
 *
 * Ceiling = [GlobalVolumePriors.maintenanceCeiling] from a personal snapshot's PV/RC when present,
 * else literature defaults (~16.5). [VolumePosture.Saturated] always counts as at ceiling.
 */
object CheckInVolumeCeiling {

    fun resolveCeiling(snapshot: MuscleVolumeResponseSnapshot?): Double =
        if (snapshot != null) {
            GlobalVolumePriors.maintenanceCeiling(
                pv = snapshot.paretoVolumePv,
                rc = snapshot.recoverabilityCeilingRc,
            )
        } else {
            GlobalVolumePriors.maintenanceCeiling()
        }

    /**
     * True when **any** primary muscle is saturated or at/above its ceiling. Empty primary set →
     * not at ceiling (no muscle to protect).
     */
    fun isAtCeiling(
        primaryMuscles: Set<MuscleGroup>,
        programVolumeByMuscle: Map<MuscleGroup, Double>,
        snapshotsByMuscle: Map<MuscleGroup, MuscleVolumeResponseSnapshot>,
    ): Boolean {
        if (primaryMuscles.isEmpty()) return false
        return primaryMuscles.any { muscle ->
            val snapshot = snapshotsByMuscle[muscle]
            if (snapshot?.posture == VolumePosture.Saturated) {
                true
            } else {
                val volume = programVolumeByMuscle[muscle] ?: 0.0
                volume >= resolveCeiling(snapshot)
            }
        }
    }
}

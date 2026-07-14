package com.stratum.domain.checkin

import com.stratum.domain.importing.GlobalVolumePriors
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.modeling.MuscleVolumeResponseSnapshot
import com.stratum.domain.response.modeling.VolumePosture

/**
 * Whether **weekly-equivalent** planned hard-set volume is high enough that opportunistic +1 set
 * is a poor lever.
 *
 * Callers must pass [WeeklyHardSetVolume] (or maps produced via
 * [WeeklyHardSetVolume.fromCycle]), not raw cycle sums. Ceiling =
 * [GlobalVolumePriors.maintenanceCeiling] from a personal snapshot's PV/RC when present, else
 * literature defaults (~16.5).
 *
 * [VolumePosture.Saturated] only forces the ceiling when planned volume is at least half the
 * maintenance ceiling — stale Saturated posture alone must not freeze soft +1s on deliberately
 * low-volume programs.
 */
object CheckInVolumeCeiling {

    /** Fraction of maintenance ceiling required before Saturated posture forces hold. */
    const val SATURATED_VOLUME_FRACTION = 0.5

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
     * True when **any** primary muscle is at/above its ceiling, or Saturated with volume at least
     * [SATURATED_VOLUME_FRACTION] of ceiling. Empty primary set → not at ceiling.
     */
    fun isAtCeiling(
        primaryMuscles: Set<MuscleGroup>,
        programVolumeByMuscle: Map<MuscleGroup, Double>,
        snapshotsByMuscle: Map<MuscleGroup, MuscleVolumeResponseSnapshot>,
    ): Boolean {
        if (primaryMuscles.isEmpty()) return false
        return primaryMuscles.any { muscle ->
            val snapshot = snapshotsByMuscle[muscle]
            val volume = programVolumeByMuscle[muscle] ?: 0.0
            val ceiling = resolveCeiling(snapshot)
            when {
                volume >= ceiling -> true
                snapshot?.posture == VolumePosture.Saturated &&
                    volume >= ceiling * SATURATED_VOLUME_FRACTION -> true
                else -> false
            }
        }
    }

    fun isAtCeiling(
        primaryMuscles: Set<MuscleGroup>,
        weeklyVolume: WeeklyHardSetVolume,
        snapshotsByMuscle: Map<MuscleGroup, MuscleVolumeResponseSnapshot>,
    ): Boolean =
        isAtCeiling(
            primaryMuscles = primaryMuscles,
            programVolumeByMuscle = weeklyVolume.byMuscle,
            snapshotsByMuscle = snapshotsByMuscle,
        )
}

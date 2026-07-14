package com.stratum.domain.checkin

import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.schedule.CycleVolumeMath

/**
 * Hard-set credits summed over one full program **cycle** (all training slots).
 * Not interchangeable with weekly landmarks (MV/PV/RC) without conversion.
 */
@JvmInline
value class CycleHardSetVolume(val byMuscle: Map<MuscleGroup, Double>) {
    fun get(muscle: MuscleGroup): Double = byMuscle[muscle] ?: 0.0
}

/**
 * Hard-set credits scaled to **weekly-equivalent** units for check-in ceilings and landmarks.
 */
@JvmInline
value class WeeklyHardSetVolume(val byMuscle: Map<MuscleGroup, Double>) {
    fun get(muscle: MuscleGroup): Double = byMuscle[muscle] ?: 0.0

    companion object {
        fun fromCycle(cycle: CycleHardSetVolume, cycleLength: Int): WeeklyHardSetVolume =
            WeeklyHardSetVolume(
                CycleVolumeMath.toWeeklyEquivalent(cycle.byMuscle, cycleLength),
            )
    }
}

package com.stratum.domain.program

import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup

private const val PRIMARY_SET_CREDIT = 1.0
private const val SECONDARY_SET_CREDIT = 0.5

internal object WorkoutMuscleVolumeAccumulator {
    fun accumulate(
        workoutLifts: List<WorkoutLiftVolumeInput>,
        liftsById: Map<LiftId, Lift>,
    ): List<MuscleVolumeSummary> {
        val volumeTotals = mutableMapOf<MuscleGroup, Double>()
        val exercisesByMuscle = mutableMapOf<MuscleGroup, MutableSet<String>>()

        for (workoutLift in workoutLifts) {
            val lift = liftsById[LiftId(workoutLift.liftId)]
            val setCount = workoutLift.setCount
            if (lift != null && setCount > 0) {
                creditMuscleVolume(
                    muscles = lift.primaryMuscles,
                    credit = PRIMARY_SET_CREDIT,
                    setCount = setCount,
                    liftId = workoutLift.liftId,
                    volumeTotals = volumeTotals,
                    exercisesByMuscle = exercisesByMuscle,
                )
                creditMuscleVolume(
                    muscles = lift.secondaryMuscles.filter { muscle -> muscle !in lift.primaryMuscles },
                    credit = SECONDARY_SET_CREDIT,
                    setCount = setCount,
                    liftId = workoutLift.liftId,
                    volumeTotals = volumeTotals,
                    exercisesByMuscle = exercisesByMuscle,
                )
            }
        }

        return volumeTotals
            .map { (muscle, setCount) ->
                MuscleVolumeSummary(
                    muscle = muscle,
                    exerciseCount = exercisesByMuscle[muscle]?.size ?: 0,
                    setCount = setCount,
                )
            }
            .sortedByDescending { summary -> summary.setCount }
    }
}

private fun creditMuscleVolume(
    muscles: Collection<MuscleGroup>,
    credit: Double,
    setCount: Int,
    liftId: String,
    volumeTotals: MutableMap<MuscleGroup, Double>,
    exercisesByMuscle: MutableMap<MuscleGroup, MutableSet<String>>,
) {
    for (muscle in muscles) {
        volumeTotals[muscle] = (volumeTotals[muscle] ?: 0.0) + setCount * credit
        exercisesByMuscle.getOrPut(muscle) { mutableSetOf() }.add(liftId)
    }
}

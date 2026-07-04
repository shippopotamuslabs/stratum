package com.stratum.domain.program

import com.stratum.domain.lift.Lift
import com.stratum.domain.onboarding.MuscleGroup
import javax.inject.Inject

private const val PRIMARY_SET_CREDIT = 1.0
private const val SECONDARY_SET_CREDIT = 0.5

/** Weekly hard-set credits and exercise counts for one muscle group on the active program. */
data class MuscleVolumeSummary(
    val muscle: MuscleGroup,
    val exerciseCount: Int,
    val setCount: Double,
)

/**
 * Computes fractional hard-set volume per muscle for a persisted [Program] using lift catalog
 * muscle tags (primary 1.0, secondary 0.5 credits per set).
 */
class ComputeProgramMuscleVolumeUseCase @Inject constructor() {
    operator fun invoke(program: Program, lifts: List<Lift>): List<MuscleVolumeSummary> {
        val liftsById = lifts.associateBy { lift -> lift.id }
        val volumeTotals = mutableMapOf<MuscleGroup, Double>()
        val exercisesByMuscle = mutableMapOf<MuscleGroup, MutableSet<String>>()

        for (workout in program.workouts) {
            for (workoutLift in workout.workoutLifts) {
                accumulateLiftVolume(
                    workoutLift = workoutLift,
                    lift = liftsById[workoutLift.liftId],
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

private fun accumulateLiftVolume(
    workoutLift: WorkoutLift,
    lift: Lift?,
    volumeTotals: MutableMap<MuscleGroup, Double>,
    exercisesByMuscle: MutableMap<MuscleGroup, MutableSet<String>>,
) {
    if (lift == null) return
    val setCount = workoutLift.sets.size
    if (setCount == 0) return
    creditMuscleVolume(
        muscles = lift.primaryMuscles,
        credit = PRIMARY_SET_CREDIT,
        setCount = setCount,
        liftId = workoutLift.liftId.value,
        volumeTotals = volumeTotals,
        exercisesByMuscle = exercisesByMuscle,
    )
    creditMuscleVolume(
        muscles = lift.secondaryMuscles.filter { muscle -> muscle !in lift.primaryMuscles },
        credit = SECONDARY_SET_CREDIT,
        setCount = setCount,
        liftId = workoutLift.liftId.value,
        volumeTotals = volumeTotals,
        exercisesByMuscle = exercisesByMuscle,
    )
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

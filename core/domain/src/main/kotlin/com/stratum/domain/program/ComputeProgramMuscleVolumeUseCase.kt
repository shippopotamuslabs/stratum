package com.stratum.domain.program

import com.stratum.domain.lift.Lift
import com.stratum.domain.onboarding.MuscleGroup
import javax.inject.Inject

/** Weekly hard-set credits and exercise counts for one muscle group. */
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
        val workoutLifts =
            program.workouts.flatMap { workout ->
                workout.workoutLifts.map { workoutLift ->
                    WorkoutLiftVolumeInput(
                        liftId = workoutLift.liftId.value,
                        setCount = workoutLift.sets.size,
                    )
                }
            }
        return WorkoutMuscleVolumeAccumulator.accumulate(workoutLifts, liftsById)
    }
}

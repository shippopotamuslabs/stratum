package com.stratum.domain.program

import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.identity.LiftId
import javax.inject.Inject

/**
 * Computes fractional hard-set volume per muscle for one workout using lift catalog
 * muscle tags (primary 1.0, secondary 0.5 credits per set).
 */
class ComputeWorkoutMuscleVolumeUseCase @Inject constructor() {
    operator fun invoke(
        workoutLifts: List<WorkoutLiftVolumeInput>,
        lifts: List<Lift>,
    ): List<MuscleVolumeSummary> {
        val liftsById = lifts.associateBy { lift -> lift.id }
        return WorkoutMuscleVolumeAccumulator.accumulate(workoutLifts, liftsById)
    }
}

package com.stratum.application.program

import com.stratum.domain.lift.Lift
import com.stratum.domain.program.MuscleVolumeSummary
import com.stratum.domain.program.Program
import com.stratum.domain.program.WorkoutLiftVolumeInput
import com.stratum.application.program.WorkoutMuscleVolumeAccumulator
import javax.inject.Inject

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

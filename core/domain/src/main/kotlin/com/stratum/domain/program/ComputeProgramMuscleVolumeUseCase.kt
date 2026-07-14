package com.stratum.domain.program

import com.stratum.domain.lift.Lift
import com.stratum.domain.onboarding.MuscleGroup
import javax.inject.Inject

/**
 * Hard-set credits and exercise counts for one muscle group over one full **cycle**
 * (sum of all training slots). Not weekly-equivalent — callers that need weekly landmarks
 * (e.g. check-in ceilings) must convert via
 * [com.stratum.domain.program.schedule.CycleVolumeMath.toWeeklyEquivalent].
 */
data class MuscleVolumeSummary(
    val muscle: MuscleGroup,
    val exerciseCount: Int,
    val setCount: Double,
)

/**
 * Computes fractional hard-set **cycle** volume per muscle for a persisted [Program] using lift
 * catalog muscle tags (primary 1.0, secondary 0.5 credits per set). Sums every training slot in
 * the template once — not calendar-week normalized.
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

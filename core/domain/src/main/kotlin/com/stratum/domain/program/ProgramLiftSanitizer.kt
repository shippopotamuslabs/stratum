package com.stratum.domain.program

import com.stratum.domain.lift.identity.LiftId

/**
 * Drops [WorkoutLift] rows whose [WorkoutLift.liftId] is not in [knownLiftIds], then removes
 * workouts that end up empty. Prevents Room FK failures on `workout_lifts.lift_id → lifts.id`.
 */
fun Program.retainOnlyKnownLifts(knownLiftIds: Set<LiftId>): Program =
    copy(
        workouts =
            workouts.mapNotNull { workout ->
                val lifts = workout.workoutLifts.filter { lift -> lift.liftId in knownLiftIds }
                if (lifts.isEmpty()) {
                    null
                } else {
                    workout.copy(workoutLifts = lifts)
                }
            },
    )

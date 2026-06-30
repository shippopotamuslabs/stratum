package com.stratum.domain.program.generation.integration

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.swap.alternativesForLift
import com.stratum.domain.program.generation.split.SessionTypeId
import com.stratum.domain.program.generation.split.inferSessionTypeFromWorkoutName

private val LOWER_FORBIDDEN_PRIMARY_PATTERNS = setOf(
    "VerticalPull",
    "HorizontalPull",
    "HorizontalPress",
    "VerticalPress",
    "InclinePress",
)

/**
 * Lift selection runs in generation cycle order, but [assignProgramSchedule] permutes workouts
 * onto weekdays afterward. Reconcile adjacent calendar sessions so the same exercise does not
 * appear on back-to-back training days (e.g. lat pulldown on Upper Pull then Lower).
 */
fun resolveAdjacentCalendarLiftCollisions(
    program: Program,
    context: ProgramGenerationContext,
): Program {
    val calendarOrder =
        program.workouts
            .filter { workout -> workout.weekday != null }
            .sortedBy { workout -> Weekday.entries.indexOf(workout.weekday) }
    if (calendarOrder.size < 2) return program

    val updatedById = program.workouts.associateBy { it.id }.toMutableMap()
    var previousLiftIds = emptySet<String>()

    for (workout in calendarOrder) {
        val resolved = resolveWorkoutCollisions(
            workout = updatedById.getValue(workout.id),
            previousLiftIds = previousLiftIds,
            context = context,
        )
        updatedById[workout.id] = resolved
        previousLiftIds = resolved.workoutLifts.map { lift -> lift.liftId.value }.toSet()
    }

    return program.copy(workouts = program.workouts.map { workout -> updatedById.getValue(workout.id) })
}

private fun resolveWorkoutCollisions(
    workout: Workout,
    previousLiftIds: Set<String>,
    context: ProgramGenerationContext,
): Workout {
    if (previousLiftIds.isEmpty()) return workout

    val lifts = workout.workoutLifts.toMutableList()
    val currentLiftIds = lifts.map { lift -> lift.liftId.value }.toMutableSet()

    for (index in lifts.indices) {
        val liftId = lifts[index].liftId.value
        if (liftId in previousLiftIds) {
            val replacement =
                alternativesForLift(context, liftId, emptyList())
                    .firstOrNull { candidate ->
                        candidate !in previousLiftIds &&
                            candidate !in currentLiftIds &&
                            isReplacementAppropriateForWorkout(workout, context.liftsById[candidate])
                    }
            if (replacement != null) {
                currentLiftIds.remove(liftId)
                currentLiftIds.add(replacement)
                lifts[index] = lifts[index].copy(liftId = LiftId(replacement))
            }
        }
    }

    return workout.copy(workoutLifts = lifts)
}

private fun isReplacementAppropriateForWorkout(
    workout: Workout,
    lift: LiftContext?,
): Boolean {
    if (lift == null) return false
    val sessionType = inferSessionTypeFromWorkoutName(workout.name) ?: return true
    if (sessionType != SessionTypeId.Lower) return true
    return lift.movementPattern !in LOWER_FORBIDDEN_PRIMARY_PATTERNS
}

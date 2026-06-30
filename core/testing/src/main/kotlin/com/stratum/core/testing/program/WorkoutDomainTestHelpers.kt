package com.stratum.core.testing.program

import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.draft.ProgramWorkoutDraft

private val LOWER_BODY_PATTERNS =
    setOf(
        "SquatPattern",
        "HipHinge",
        "KneeExtension",
        "KneeFlexion",
        "PlantarFlexion",
        "HipExtension",
    )

/** True when the workout's selected lifts include a lower-body primary pattern. */
fun isLowerDominantWorkout(
    workout: ProgramWorkoutDraft,
    context: ProgramGenerationContext,
): Boolean =
    workout.workoutLifts.orEmpty().any { lift ->
        val pattern = context.movementPatternByLiftId[lift.liftId] ?: return@any false
        pattern in LOWER_BODY_PATTERNS
    }

fun lowerDominantWorkouts(
    draft: com.stratum.domain.program.generation.draft.ProgramDraft,
    context: ProgramGenerationContext,
): List<ProgramWorkoutDraft> = draft.workouts.filter { workout -> isLowerDominantWorkout(workout, context) }

package com.stratum.domain.training

/**
 * Navigation / process-surface identity for a live session.
 *
 * Program workouts use the template [TrainingSession.workoutId]. Ad-hoc empty sessions have
 * null `workoutId`, so the session id is used as the route key (same convention as
 * notification [ActiveWorkoutTracker] surfaces).
 */
fun TrainingSession.liveWorkoutRouteId(): String =
    workoutId?.value ?: id.value

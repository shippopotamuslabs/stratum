package com.stratum.domain.training

/**
 * Read model for the workout completion screen. Carries the session duration and the
 * best set per lift (highest weight × reps) so the user gets a quick recap of what they did.
 *
 * PR detection is deferred to a follow-up slice — [LiftBestSet] carries no PR flag for now.
 */
data class WorkoutSummary(
    val workoutName: String,
    val durationMillis: Long,
    val bestSets: List<LiftBestSet>,
)

data class LiftBestSet(
    val liftName: String,
    val weight: Double,
    val reps: Int,
    val rir: Int?,
    val isPersonalRecord: Boolean,
    val estimatedOneRm: Double?,
)

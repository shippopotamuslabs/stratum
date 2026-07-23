package com.stratum.domain.training

import com.stratum.domain.lift.Weight

/**
 * Read model for the workout completion screen. Carries the session duration and the
 * best set per lift (highest weight × reps) so the user gets a quick recap of what they did.
 *
 * [LiftBestSet.weight] preserves the unit the set was logged/imported with.
 * [estimatedOneRm] is a magnitude in the **same unit** as [weight] (null when unknown).
 */
data class WorkoutSummary(
    val workoutName: String,
    val durationMillis: Long,
    val bestSets: List<LiftBestSet>,
)

data class LiftBestSet(
    val liftName: String,
    val weight: Weight,
    val reps: Int,
    val rir: Int?,
    val isPersonalRecord: Boolean,
    val estimatedOneRm: Double?,
)

package com.stratum.feature.program.navigation

import kotlinx.serialization.Serializable

/**
 * How [LiftSwapKey] commits a selected lift.
 *
 * - [EditSession]: emit on [com.stratum.domain.workout.WorkoutEditSession] for Edit / Create / live Workout drafts.
 * - [PersistToProgram]: call [com.stratum.application.program.SwapWorkoutLiftUseCase] (Programs tab one-tap swap).
 */
@Serializable
enum class LiftSwapCommitMode {
    EditSession,
    PersistToProgram,
}

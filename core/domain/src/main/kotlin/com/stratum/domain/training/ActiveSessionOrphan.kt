package com.stratum.domain.training

import com.stratum.domain.program.Program

/**
 * True when this in-progress session can no longer be resumed against the active [program]
 * template — e.g. the bound workout was deleted mid-session, or a different program is active.
 *
 * Orphaned sessions block [StartTrainingSessionUseCase] and leave the live-workout screen stuck
 * on loading if the UI assumes the template still exists. Callers should abandon them.
 */
fun TrainingSession.isOrphanedAgainstActiveProgram(program: Program?): Boolean {
    if (status != TrainingSessionStatus.IN_PROGRESS) return false
    return when (kind) {
        TrainingSessionKind.PROGRAM_WORKOUT -> {
            val boundWorkoutId = workoutId ?: return true
            when {
                program == null -> true
                programId != null && programId != program.id -> true
                program.workouts.none { it.id == boundWorkoutId } -> true
                else -> false
            }
        }
        TrainingSessionKind.AD_HOC ->
            program == null || (programId != null && programId != program.id)
        // Rest-day rows are ledger markers, not live interactive sessions.
        TrainingSessionKind.REST_DAY -> false
    }
}

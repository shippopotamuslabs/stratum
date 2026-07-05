package com.stratum.domain.program

import com.stratum.domain.program.identity.WorkoutId
import javax.inject.Inject

/**
 * Persists edits to a single workout template by merging into the active [Program] and
 * replacing the full hierarchy via [ProgramRepository.saveProgram].
 */
class SaveWorkoutUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
) {
    suspend operator fun invoke(workoutId: WorkoutId, updatedWorkout: Workout): Result<Unit> {
        val program =
            programRepository.getProgram()
                ?: return Result.failure(IllegalStateException("No active program found to save workout"))
        return runCatching {
            val merged = program.mergeWorkout(workoutId, updatedWorkout)
            programRepository.saveProgram(merged)
        }
    }
}

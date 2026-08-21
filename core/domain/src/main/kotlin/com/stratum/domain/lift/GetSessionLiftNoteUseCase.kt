package com.stratum.domain.lift

import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.identity.TrainingSessionId
import javax.inject.Inject

/** Loads the current session note for one workout-lift slot, if any. */
class GetSessionLiftNoteUseCase @Inject constructor(
    private val liftNotesRepository: LiftNotesRepository,
) {
    suspend operator fun invoke(
        sessionId: TrainingSessionId,
        workoutLiftId: WorkoutLiftId,
    ): LiftSessionNote? = liftNotesRepository.getSessionNote(sessionId, workoutLiftId)
}

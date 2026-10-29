package com.stratum.application.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.training.identity.TrainingSessionId
import javax.inject.Inject
import com.stratum.domain.lift.PastSessionLiftNote

/** Loads completed-session notes for a catalog lift (newest first). */
class GetPastSessionNotesForLiftUseCase @Inject constructor(
    private val liftNotesRepository: LiftNotesRepository,
) {
    suspend operator fun invoke(
        liftId: LiftId,
        excludeSessionId: TrainingSessionId?,
    ): List<PastSessionLiftNote> =
        liftNotesRepository.getPastNotesForLift(liftId, excludeSessionId)
}

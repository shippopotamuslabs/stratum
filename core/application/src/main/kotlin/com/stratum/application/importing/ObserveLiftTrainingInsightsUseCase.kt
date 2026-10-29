package com.stratum.application.importing

import com.stratum.domain.importing.FatigueDecay
import com.stratum.domain.lift.identity.LiftId
import javax.inject.Inject

class ObserveLiftTrainingInsightsUseCase @Inject constructor(
    private val repository: LiftTrainingInsightsRepository,
) {
    suspend operator fun invoke(): Map<LiftId, FatigueDecay> =
        repository.getAll().associate { it.liftId to FatigueDecay(it.fatigueDecaySlope, it.fatigueDecayIntercept) }
}

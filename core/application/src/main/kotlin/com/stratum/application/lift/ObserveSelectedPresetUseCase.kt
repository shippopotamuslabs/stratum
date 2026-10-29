package com.stratum.application.lift

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import com.stratum.domain.lift.GymPreset

/**
 * Reads the gym-preset stream chosen in the Gym & Lifts step. `null` until the user picks a
 * preset; OS-5 Review renders the name from this stream.
 */
class ObserveSelectedPresetUseCase @Inject constructor(
    private val repository: LiftRepository,
) {
    operator fun invoke(): Flow<GymPreset?> = repository.observeSelectedPreset()
}

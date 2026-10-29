package com.stratum.application.lift

import javax.inject.Inject
import com.stratum.domain.lift.GymPreset

/**
 * Records (or clears, with `null`) the user's preset choice. Called when the user selects a
 * preset (alongside [ApplyGymPresetUseCase]) or when they manually deviate from a preset's
 * defaults and the chip should deselect. OS-5 Review renders the selected preset's name.
 */
class SetSelectedPresetUseCase @Inject constructor(
    private val repository: LiftRepository,
) {
    suspend operator fun invoke(preset: GymPreset?): Result<Unit> =
        runCatching {
            repository.setSelectedPreset(preset)
        }
}

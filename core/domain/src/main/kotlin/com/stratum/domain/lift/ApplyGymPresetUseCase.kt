package com.stratum.domain.lift

import javax.inject.Inject

/**
 * Enables every lift tagged with [preset] and records the preset choice. The single entry
 * point ViewModels use to apply a preset — they never call [LiftRepository] directly
 * (ARCHITECTURE.md §4.4).
 */
class ApplyGymPresetUseCase @Inject constructor(
    private val repository: LiftRepository,
) {
    suspend operator fun invoke(preset: GymPreset): Result<Unit> =
        runCatching {
            repository.applyPreset(preset)
            repository.setSelectedPreset(preset)
        }
}

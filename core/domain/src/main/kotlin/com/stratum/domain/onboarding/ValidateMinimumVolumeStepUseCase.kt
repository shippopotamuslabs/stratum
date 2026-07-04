package com.stratum.domain.onboarding

import javax.inject.Inject

sealed interface MinimumVolumeValidationResult {
    data object Valid : MinimumVolumeValidationResult
    data class Invalid(val errors: Set<MinimumVolumeValidationError>) : MinimumVolumeValidationResult
}

enum class MinimumVolumeValidationError {
    TooManyMinimumVolume,
    OverlapsSpecialization,
}

/**
 * Validates the Minimum Volume step. Skip (empty selection) is valid.
 */
class ValidateMinimumVolumeStepUseCase @Inject constructor() {
    operator fun invoke(preference: SpecializationPreference): MinimumVolumeValidationResult {
        val errors = buildSet {
            if (preference.minimumVolumeMuscles.size > MAX_MINIMUM_VOLUME) {
                add(MinimumVolumeValidationError.TooManyMinimumVolume)
            }
            if (preference.specializedMuscles.intersect(preference.minimumVolumeMuscles).isNotEmpty()) {
                add(MinimumVolumeValidationError.OverlapsSpecialization)
            }
        }
        return if (errors.isEmpty()) {
            MinimumVolumeValidationResult.Valid
        } else {
            MinimumVolumeValidationResult.Invalid(errors)
        }
    }

    companion object {
        const val MAX_MINIMUM_VOLUME = 8
    }
}

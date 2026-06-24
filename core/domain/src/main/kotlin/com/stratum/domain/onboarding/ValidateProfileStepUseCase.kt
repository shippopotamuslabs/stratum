package com.stratum.domain.onboarding

import javax.inject.Inject

/**
 * Validates the Profile step of onboarding. Pure (no I/O) so it can run synchronously
 * off a draft snapshot. Both [UserTrainingProfile.trainingAge] and
 * [UserTrainingProfile.lastTrained] are required.
 */
class ValidateProfileStepUseCase @Inject constructor() {
    operator fun invoke(profile: UserTrainingProfile): ProfileValidationResult {
        val errors = buildSet {
            if (profile.trainingAge == null) add(ProfileValidationError.TrainingAgeRequired)
            if (profile.lastTrained == null) add(ProfileValidationError.LastTrainedRequired)
        }
        return if (errors.isEmpty()) {
            ProfileValidationResult.Valid
        } else {
            ProfileValidationResult.Invalid(errors)
        }
    }
}

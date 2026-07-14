package com.stratum.core.ui.trainingsetup

import com.stratum.domain.onboarding.ValidateMinimumVolumeStepUseCase
import com.stratum.domain.onboarding.ValidateScheduleStepUseCase
import com.stratum.domain.onboarding.ValidateSpecializationStepUseCase

/**
 * Domain validation bounds surfaced to training-setup presentation (onboarding + create program)
 * without duplicating magic numbers in draft UIs.
 */
object TrainingSetupValidationConstants {
    const val MIN_SESSION_MINUTES: Int = ValidateScheduleStepUseCase.MIN_SESSION_MINUTES
    const val MAX_SESSION_MINUTES: Int = ValidateScheduleStepUseCase.MAX_SESSION_MINUTES
    const val MAX_TRAINING_DAYS: Int = ValidateScheduleStepUseCase.MAX_TRAINING_DAYS
    const val MAX_SPECIALIZE: Int = ValidateSpecializationStepUseCase.MAX_SPECIALIZE
    const val MAX_MINIMUM_VOLUME: Int = ValidateMinimumVolumeStepUseCase.MAX_MINIMUM_VOLUME
}

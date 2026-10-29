package com.stratum.feature.onboarding

import com.stratum.core.ui.trainingsetup.TrainingSetupValidationConstants
import com.stratum.application.lift.ValidateGymLiftsStepUseCase

/**
 * Domain validation bounds for onboarding presentation. Training-setup caps come from shared
 * [TrainingSetupValidationConstants]; gym-lift min is onboarding-only.
 */
object OnboardingValidationConstants {
    const val MIN_SESSION_MINUTES: Int = TrainingSetupValidationConstants.MIN_SESSION_MINUTES
    const val MAX_SESSION_MINUTES: Int = TrainingSetupValidationConstants.MAX_SESSION_MINUTES
    const val MAX_SPECIALIZE: Int = TrainingSetupValidationConstants.MAX_SPECIALIZE
    const val MAX_MINIMUM_VOLUME: Int = TrainingSetupValidationConstants.MAX_MINIMUM_VOLUME
    const val MIN_ENABLED_LIFTS: Int = ValidateGymLiftsStepUseCase.MIN_ENABLED
}

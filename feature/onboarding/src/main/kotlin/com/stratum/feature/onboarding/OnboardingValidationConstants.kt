package com.stratum.feature.onboarding

import com.stratum.domain.onboarding.ValidateScheduleStepUseCase
import com.stratum.domain.onboarding.ValidateMinimumVolumeStepUseCase
import com.stratum.domain.onboarding.ValidateSpecializationStepUseCase
import com.stratum.domain.lift.ValidateGymLiftsStepUseCase

/**
 * Domain validation bounds surfaced to presentation without duplicating magic numbers in
 * [ScheduleDraftUi] or [SpecializationDraftUi].
 */
object OnboardingValidationConstants {
    const val MIN_SESSION_MINUTES: Int = ValidateScheduleStepUseCase.MIN_SESSION_MINUTES
    const val MAX_SESSION_MINUTES: Int = ValidateScheduleStepUseCase.MAX_SESSION_MINUTES
    const val MAX_SPECIALIZE: Int = ValidateSpecializationStepUseCase.MAX_SPECIALIZE
    const val MAX_MINIMUM_VOLUME: Int = ValidateMinimumVolumeStepUseCase.MAX_MINIMUM_VOLUME
    const val MIN_ENABLED_LIFTS: Int = ValidateGymLiftsStepUseCase.MIN_ENABLED
}

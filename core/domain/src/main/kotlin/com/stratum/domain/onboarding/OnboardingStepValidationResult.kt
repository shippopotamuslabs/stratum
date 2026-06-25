package com.stratum.domain.onboarding

/** Outcome of validating the current onboarding step. Single source for canAdvance and next(). */
sealed interface OnboardingStepValidationResult {
    data object Valid : OnboardingStepValidationResult
    data object Invalid : OnboardingStepValidationResult

    val isValid: Boolean get() = this is Valid
}

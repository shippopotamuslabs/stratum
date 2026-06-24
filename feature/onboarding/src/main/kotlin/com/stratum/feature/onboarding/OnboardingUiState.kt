package com.stratum.feature.onboarding

import com.stratum.domain.onboarding.OnboardingStep

/**
 * Presentation model for the onboarding wizard. Domain `OnboardingStep` is mapped
 * to a presentation-friendly form here; composables never receive the domain type
 * (ARCHITECTURE.md §4.1).
 */
data class OnboardingUiState(
    val step: OnboardingStep,
    val isComplete: Boolean = false,
) {
    val isFirst: Boolean get() = step.isFirst
    val isLast: Boolean get() = step.isLast
    val canAdvance: Boolean get() = step.next() != null
    val canGoBack: Boolean get() = step.previous() != null

    companion object {
        val Initial = OnboardingUiState(step = OnboardingStep.First)
    }
}

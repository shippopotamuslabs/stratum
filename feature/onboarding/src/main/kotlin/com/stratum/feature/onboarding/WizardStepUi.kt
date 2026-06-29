package com.stratum.feature.onboarding

import com.stratum.domain.onboarding.OnboardingStep

/**
 * Presentation model for one wizard step. Composables never receive domain [OnboardingStep]
 * (ARCHITECTURE.md §4.3).
 */
data class WizardStepUi(
    val id: String,
    val displayName: String,
    val index: Int,
    val isFirst: Boolean,
    val isLast: Boolean,
    val stepIndicatorCount: Int,
)

internal fun OnboardingStep.toWizardStepUi(): WizardStepUi {
    val entries = OnboardingStep.entries
    return WizardStepUi(
        id = name,
        displayName = toStepDisplayName(),
        index = ordinal,
        isFirst = isFirst,
        isLast = isLast,
        stepIndicatorCount = entries.size,
    )
}

internal fun allWizardSteps(): List<WizardStepUi> =
    OnboardingStep.entries.map { it.toWizardStepUi() }

internal fun OnboardingStep.toStepDisplayName(): String = when (this) {
    OnboardingStep.Welcome -> "Welcome"
    OnboardingStep.Profile -> "Profile"
    OnboardingStep.Schedule -> "Schedule"
    OnboardingStep.Specialization -> "Specialization"
    OnboardingStep.GymAndLifts -> "Gym & Lifts"
    OnboardingStep.Review -> "Review"
    OnboardingStep.Generate -> "Create Program"
}

internal fun wizardStepFromId(id: String): WizardStepUi? =
    OnboardingStep.entries.firstOrNull { it.name == id }?.toWizardStepUi()

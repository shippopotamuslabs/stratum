package com.stratum.feature.onboarding

import com.stratum.domain.onboarding.OnboardingPath
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

/**
 * The steps shown for a given [path]. `null` covers the common prefix (before the user chooses at
 * [OnboardingStep.ChoosePath]). The Import path skips Schedule/Specialization/MinimumVolume.
 */
internal fun stepsForPath(path: OnboardingPath?): List<OnboardingStep> = when (path) {
    null -> listOf(
        OnboardingStep.Profile,
        OnboardingStep.GymAndLifts,
        OnboardingStep.ChoosePath,
    )
    OnboardingPath.Import -> listOf(
        OnboardingStep.Profile,
        OnboardingStep.GymAndLifts,
        OnboardingStep.ChoosePath,
        OnboardingStep.Review,
    )
    OnboardingPath.Generate -> listOf(
        OnboardingStep.Profile,
        OnboardingStep.GymAndLifts,
        OnboardingStep.ChoosePath,
        OnboardingStep.Schedule,
        OnboardingStep.Specialization,
        OnboardingStep.MinimumVolume,
        OnboardingStep.Review,
    )
}

internal fun OnboardingStep.toWizardStepUi(path: OnboardingPath? = null): WizardStepUi {
    val steps = stepsForPath(path)
    val indexInPath = steps.indexOf(this).let { if (it == -1) ordinal else it }
    return WizardStepUi(
        id = name,
        displayName = toStepDisplayName(),
        index = indexInPath,
        isFirst = isFirst,
        isLast = isLast,
        stepIndicatorCount = steps.size,
    )
}

internal fun allWizardSteps(path: OnboardingPath? = null): List<WizardStepUi> =
    stepsForPath(path).map { it.toWizardStepUi(path) }

internal fun OnboardingStep.toStepDisplayName(): String = when (this) {
    OnboardingStep.Profile -> "Profile"
    OnboardingStep.GymAndLifts -> "Gym & Lifts"
    OnboardingStep.ChoosePath -> "Program Option"
    OnboardingStep.Schedule -> "Schedule"
    OnboardingStep.Specialization -> "Specialization"
    OnboardingStep.MinimumVolume -> "Minimum Volume"
    OnboardingStep.Review -> "Review"
}

internal fun wizardStepFromId(id: String, path: OnboardingPath? = null): WizardStepUi? =
    OnboardingStep.entries.firstOrNull { it.name == id }?.toWizardStepUi(path)

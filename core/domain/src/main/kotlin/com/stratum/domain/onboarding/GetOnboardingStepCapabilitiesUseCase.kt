package com.stratum.domain.onboarding

import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.Lift
import javax.inject.Inject

class GetOnboardingStepCapabilitiesUseCase @Inject constructor(
    private val validateCurrentStep: ValidateCurrentOnboardingStepUseCase,
) {
    operator fun invoke(
        step: OnboardingStep,
        profile: UserTrainingProfile,
        specialization: SpecializationPreference,
        lifts: List<Lift>,
        selectedPreset: GymPreset? = null,
        usedHistoricalImport: Boolean = false,
        path: OnboardingPath? = null,
    ): OnboardingStepCapabilities {
        val resolvedPath = path ?: resolvePath(step, profile)
        val stepValid =
            validateCurrentStep(
                step = step,
                profile = profile,
                specialization = specialization,
                lifts = lifts,
                selectedPreset = selectedPreset,
                usedHistoricalImport = usedHistoricalImport,
            ).isValid
        val gymLiftsValid =
            validateCurrentStep(
                step = OnboardingStep.GymAndLifts,
                profile = profile,
                specialization = specialization,
                lifts = lifts,
                selectedPreset = selectedPreset,
                usedHistoricalImport = usedHistoricalImport,
            ).isValid
        return OnboardingStepCapabilities(
            stepValid = stepValid,
            gymLiftsValid = gymLiftsValid,
            canGoBack = step.previous(resolvedPath) != null,
        )
    }

    private fun resolvePath(step: OnboardingStep, profile: UserTrainingProfile): OnboardingPath? =
        when (step) {
            OnboardingStep.Welcome,
            OnboardingStep.Profile,
            OnboardingStep.GymAndLifts,
            OnboardingStep.ChoosePath,
            -> null
            OnboardingStep.Schedule,
            OnboardingStep.Specialization,
            OnboardingStep.MinimumVolume,
            -> OnboardingPath.Generate
            OnboardingStep.Review ->
                if (profile.weekdays.isEmpty()) OnboardingPath.Import else OnboardingPath.Generate
        }
}

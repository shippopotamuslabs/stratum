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
    ): OnboardingStepCapabilities {
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
            canGoBack = step.previous() != null,
        )
    }
}

package com.stratum.application.onboarding

import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.Lift
import javax.inject.Inject
import com.stratum.domain.onboarding.OnboardingPath
import com.stratum.domain.onboarding.OnboardingPathResolver
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.onboarding.OnboardingStepCapabilities
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.UserTrainingProfile

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
        cycleLength: Int? = null,
        restSlotIndices: Set<Int>? = null,
        trainingDayCount: Int? = null,
        persistedSelectedPath: OnboardingPath? = null,
    ): OnboardingStepCapabilities {
        val resolvedPath =
            path
                ?: OnboardingPathResolver.resolve(
                    step = step,
                    selectedPath = persistedSelectedPath,
                    profile = profile,
                    trainingDayCount = trainingDayCount,
                    restSlotIndices = restSlotIndices,
                    cycleLength = cycleLength,
                )
        val stepValid =
            validateCurrentStep(
                step = step,
                profile = profile,
                specialization = specialization,
                lifts = lifts,
                selectedPreset = selectedPreset,
                usedHistoricalImport = usedHistoricalImport,
                cycleLength = cycleLength,
                restSlotIndices = restSlotIndices,
                trainingDayCount = trainingDayCount,
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
}

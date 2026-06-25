package com.stratum.domain.onboarding

import com.stratum.domain.lift.GymLiftsValidationResult
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.ValidateGymLiftsStepUseCase
import javax.inject.Inject

/**
 * Validates the current onboarding step against profile, specialization, and lifts.
 * Pure (no I/O) — used for both canAdvance gating and forward navigation.
 */
class ValidateCurrentOnboardingStepUseCase @Inject constructor(
    private val validateProfileStep: ValidateProfileStepUseCase,
    private val validateScheduleStep: ValidateScheduleStepUseCase,
    private val validateSpecializationStep: ValidateSpecializationStepUseCase,
    private val validateGymLiftsStep: ValidateGymLiftsStepUseCase,
) {
    operator fun invoke(
        step: OnboardingStep,
        profile: UserTrainingProfile,
        specialization: SpecializationPreference,
        lifts: List<Lift>,
        selectedPreset: GymPreset? = null,
    ): OnboardingStepValidationResult {
        val valid = when (step) {
            OnboardingStep.Profile ->
                validateProfileStep(profile) is ProfileValidationResult.Valid
            OnboardingStep.Schedule ->
                validateScheduleStep(profile) is ScheduleValidationResult.Valid
            OnboardingStep.Specialization ->
                validateSpecializationStep(specialization) is SpecializationValidationResult.Valid
            OnboardingStep.GymAndLifts -> isGymLiftsStepValid(lifts, selectedPreset)
            else -> true
        }
        return if (valid) {
            OnboardingStepValidationResult.Valid
        } else {
            OnboardingStepValidationResult.Invalid
        }
    }

    private fun isGymLiftsStepValid(lifts: List<Lift>, selectedPreset: GymPreset?): Boolean {
        val liftsValid = validateGymLiftsStep(lifts) is GymLiftsValidationResult.Valid
        val enabledCount = lifts.count { it.enabled }
        return enabledCount >= ValidateGymLiftsStepUseCase.MIN_ENABLED ||
            (selectedPreset != null && liftsValid)
    }
}

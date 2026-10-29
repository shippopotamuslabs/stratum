package com.stratum.application.onboarding

import com.stratum.domain.lift.GymLiftsValidationResult
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.Lift
import com.stratum.application.lift.ValidateGymLiftsStepUseCase
import javax.inject.Inject
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.onboarding.OnboardingStepValidationResult
import com.stratum.domain.onboarding.ProfileValidationResult
import com.stratum.domain.onboarding.ScheduleValidationResult
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.SpecializationValidationResult
import com.stratum.domain.onboarding.UserTrainingProfile

/**
 * Validates the current onboarding step against profile, specialization, and lifts.
 * Pure (no I/O) — used for both canAdvance gating and forward navigation.
 */
class ValidateCurrentOnboardingStepUseCase @Inject constructor(
    private val validateProfileStep: ValidateProfileStepUseCase,
    private val validateScheduleStep: ValidateScheduleStepUseCase,
    private val validateSpecializationStep: ValidateSpecializationStepUseCase,
    private val validateMinimumVolumeStep: ValidateMinimumVolumeStepUseCase,
    private val validateGymLiftsStep: ValidateGymLiftsStepUseCase,
) {
    operator fun invoke(
        step: OnboardingStep,
        profile: UserTrainingProfile,
        specialization: SpecializationPreference,
        lifts: List<Lift>,
        selectedPreset: GymPreset? = null,
        usedHistoricalImport: Boolean = false,
        /** Optional gen cycle-space; not persisted on profile. */
        cycleLength: Int? = null,
        restSlotIndices: Set<Int>? = null,
        /** Explicit train-day count N when weekdays are not schedule identity. */
        trainingDayCount: Int? = null,
    ): OnboardingStepValidationResult {
        val valid = when (step) {
            OnboardingStep.Profile ->
                validateProfileStep(profile) is ProfileValidationResult.Valid
            OnboardingStep.Schedule ->
                validateScheduleStep(
                    profile = profile,
                    cycleLength = cycleLength,
                    restSlotIndices = restSlotIndices,
                    trainingDayCount = trainingDayCount,
                ) is ScheduleValidationResult.Valid
            OnboardingStep.Specialization ->
                validateSpecializationStep(specialization) is SpecializationValidationResult.Valid
            OnboardingStep.MinimumVolume ->
                validateMinimumVolumeStep(specialization) is MinimumVolumeValidationResult.Valid
            OnboardingStep.GymAndLifts ->
                isGymLiftsStepValid(lifts, selectedPreset, usedHistoricalImport)
            else -> true
        }
        return if (valid) {
            OnboardingStepValidationResult.Valid
        } else {
            OnboardingStepValidationResult.Invalid
        }
    }

    private fun isGymLiftsStepValid(
        lifts: List<Lift>,
        selectedPreset: GymPreset?,
        usedHistoricalImport: Boolean,
    ): Boolean {
        val liftsValid = validateGymLiftsStep(lifts) is GymLiftsValidationResult.Valid
        val hasPath = selectedPreset != null || usedHistoricalImport
        return hasPath && liftsValid
    }
}

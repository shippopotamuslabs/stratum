package com.stratum.domain.onboarding

import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.Lift
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject

/** Direction of wizard step navigation. */
enum class OnboardingStepDirection {
    Forward,
    Backward,
}

/** Outcome of attempting to advance or retreat in the wizard. */
sealed interface AdvanceOnboardingStepResult {
    data class Success(val newStep: OnboardingStep) : AdvanceOnboardingStepResult
    data object ValidationFailed : AdvanceOnboardingStepResult
    data object NoOp : AdvanceOnboardingStepResult

    /** Profile / specialization / progress I/O failed after validation passed. */
    data object PersistenceFailed : AdvanceOnboardingStepResult
}

/**
 * Encapsulates the triple-write on step navigation: validate (forward only), persist profile,
 * persist specialization, save new step.
 *
 * Navigation is path-aware: after the [OnboardingStep.ChoosePath] fork, the [path] determines
 * whether the user walks the Generate-only steps or jumps straight to Review. When the user commits
 * to the Import path (advancing from ChoosePath to Review), any schedule/specialization/minimum-volume
 * data from a prior Generate-path exploration is cleared so it doesn't persist on completion.
 */
class AdvanceOnboardingStepUseCase @Inject constructor(
    private val validateCurrentStep: ValidateCurrentOnboardingStepUseCase,
    private val updateUserTrainingProfile: UpdateUserTrainingProfileUseCase,
    private val updateSpecialization: UpdateSpecializationUseCase,
    private val saveOnboardingProgress: SaveOnboardingProgressUseCase,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(
        currentStep: OnboardingStep,
        direction: OnboardingStepDirection,
        profile: UserTrainingProfile,
        specialization: SpecializationPreference,
        lifts: List<Lift>,
        selectedPreset: GymPreset?,
        usedHistoricalImport: Boolean = false,
        path: OnboardingPath? = null,
    ): AdvanceOnboardingStepResult {
        val resolvedPath = path ?: resolvePath(currentStep, profile)
        val targetStep = when (direction) {
            OnboardingStepDirection.Forward -> currentStep.next(resolvedPath)
            OnboardingStepDirection.Backward -> currentStep.previous(resolvedPath)
        } ?: return AdvanceOnboardingStepResult.NoOp

        if (direction == OnboardingStepDirection.Forward) {
            val validation = validateCurrentStep(
                step = currentStep,
                profile = profile,
                specialization = specialization,
                lifts = lifts,
                selectedPreset = selectedPreset,
                usedHistoricalImport = usedHistoricalImport,
            )
            if (!validation.isValid) {
                return AdvanceOnboardingStepResult.ValidationFailed
            }
        }

        // Clearing the Generate-path inputs when committing to Import keeps the persisted profile
        // and specialization clean — the Review summary hides those cards, and completion won't
        // carry stale schedule/specialization data forward.
        val isCommittingToImport =
            currentStep == OnboardingStep.ChoosePath && resolvedPath == OnboardingPath.Import
        val profileToSave = if (isCommittingToImport) {
            profile.copy(weekdays = emptySet(), sessionLengthMinutes = null)
        } else {
            profile
        }
        val specializationToSave = if (isCommittingToImport) {
            SpecializationPreference.Empty
        } else {
            specialization
        }

        val persistResult =
            runCatchingLogged(loggingPort, TAG, "Failed to advance onboarding step") {
                updateUserTrainingProfile(profileToSave)
                updateSpecialization(specializationToSave)
                saveOnboardingProgress(targetStep)
            }
        return if (persistResult.isSuccess) {
            AdvanceOnboardingStepResult.Success(targetStep)
        } else {
            AdvanceOnboardingStepResult.PersistenceFailed
        }
    }

    /**
     * Best-effort path resolution when the caller doesn't pass one explicitly. Mirrors
     * [OnboardingWizardState.onboardingPath] using the current step and profile. Returns `null`
     * (common prefix) when the step is at or before ChoosePath.
     */
    private fun resolvePath(currentStep: OnboardingStep, profile: UserTrainingProfile): OnboardingPath? =
        when (currentStep) {
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

    private companion object {
        const val TAG = "AdvanceOnboardingStep"
    }
}

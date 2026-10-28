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
 * persist specialization, save new step (+ durable [OnboardingPath]).
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
        /** Generate cycle-space; persisted onto the profile on Generate schedule flush. */
        cycleLength: Int? = null,
        restSlotIndices: Set<Int>? = null,
        trainingDayCount: Int? = null,
        /** Durable path from [OnboardingProgress.selectedPath] when not re-passed by the caller. */
        persistedSelectedPath: OnboardingPath? = null,
    ): AdvanceOnboardingStepResult {
        val resolvedPath =
            path
                ?: OnboardingPathResolver.resolve(
                    step = currentStep,
                    selectedPath = persistedSelectedPath,
                    profile = profile,
                    trainingDayCount = trainingDayCount,
                    restSlotIndices = restSlotIndices,
                    cycleLength = cycleLength,
                )
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
                cycleLength = cycleLength,
                restSlotIndices = restSlotIndices,
                trainingDayCount = trainingDayCount,
            )
            if (!validation.isValid) {
                return AdvanceOnboardingStepResult.ValidationFailed
            }
        }

        val isCommittingToImport =
            currentStep == OnboardingStep.ChoosePath && resolvedPath == OnboardingPath.Import
        // Persist cycle-space when leaving Generate schedule steps; clear synthetic weekdays.
        val isGenerateScheduleFlush =
            resolvedPath == OnboardingPath.Generate &&
                currentStep in
                setOf(
                    OnboardingStep.Schedule,
                    OnboardingStep.Specialization,
                    OnboardingStep.MinimumVolume,
                )
        val profileToSave = when {
            isCommittingToImport -> profile.clearedScheduleIdentity()
            isGenerateScheduleFlush ->
                profile.withGenerateCycleSpace(
                    trainingDayCount = trainingDayCount,
                    cycleLength = cycleLength,
                    restSlotIndices = restSlotIndices.orEmpty(),
                )
            else -> profile
        }
        val specializationToSave = if (isCommittingToImport) {
            SpecializationPreference.Empty
        } else {
            specialization
        }
        // Write path when known (ChoosePath commit or any Generate step); preserve when null.
        val pathToPersist =
            when {
                currentStep == OnboardingStep.ChoosePath &&
                    direction == OnboardingStepDirection.Forward &&
                    resolvedPath != null -> resolvedPath
                resolvedPath != null -> resolvedPath
                else -> null
            }

        val persistResult =
            runCatchingLogged(loggingPort, TAG, "Failed to advance onboarding step") {
                updateUserTrainingProfile(profileToSave)
                updateSpecialization(specializationToSave)
                saveOnboardingProgress(targetStep, selectedPath = pathToPersist)
            }
        return if (persistResult.isSuccess) {
            AdvanceOnboardingStepResult.Success(targetStep)
        } else {
            AdvanceOnboardingStepResult.PersistenceFailed
        }
    }

    private companion object {
        const val TAG = "AdvanceOnboardingStep"
    }
}

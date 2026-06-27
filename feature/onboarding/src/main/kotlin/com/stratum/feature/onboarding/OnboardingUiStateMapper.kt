package com.stratum.feature.onboarding

import com.stratum.domain.importing.ImportProgressPhase
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.onboarding.OnboardingWizardState
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.onboarding.ValidateCurrentOnboardingStepUseCase

/**
 * Pure domain + ephemeral → [OnboardingUiState] mapping. Keeps [OnboardingViewModel] focused on
 * orchestration (mirrors [ProfileDraftMapping] and sibling draft mappers).
 */
internal fun buildOnboardingUiState(
    current: OnboardingWizardState,
    profile: UserTrainingProfile,
    sessionLengthEdit: SessionLengthEdit,
    specialization: SpecializationPreference,
    ephemeral: OnboardingEphemeralState,
    validateCurrentStep: ValidateCurrentOnboardingStepUseCase,
): OnboardingUiState {
    val step = current.currentStep.toWizardStepUi()
    val lifts = current.lifts
    val preset = current.selectedPreset
    val stepValid = validateCurrentStep(
        step = current.currentStep,
        profile = profile,
        specialization = specialization,
        lifts = lifts,
        selectedPreset = preset,
        usedHistoricalImport = current.usedHistoricalImport,
    ).isValid
    val importing = ephemeral.importProgress?.let { progress ->
        progress.phase != ImportProgressPhase.Complete &&
            progress.phase != ImportProgressPhase.Failed
    } == true
    val canAdvance = !ephemeral.applyingPreset && !importing && stepValid
    val gymLiftsValid = validateCurrentStep(
        step = OnboardingStep.GymAndLifts,
        profile = profile,
        specialization = specialization,
        lifts = lifts,
        selectedPreset = preset,
        usedHistoricalImport = current.usedHistoricalImport,
    ).isValid

    return OnboardingUiState(
        step = step,
        isComplete = current.isComplete,
        canAdvance = canAdvance,
        canGoBack = current.currentStep.previous() != null,
        profileDraft = profile.toProfileDraft().copy(showErrors = ephemeral.showErrors),
        scheduleDraft = profile.toScheduleDraft(sessionLengthEdit).copy(showErrors = ephemeral.showErrors),
        specializationDraft = specialization.toSpecializationDraft().copy(showErrors = ephemeral.showErrors),
        gymLiftsDraft = lifts.toGymLiftsDraft(
            selectedPreset = preset,
            usedHistoricalImport = current.usedHistoricalImport,
            hasCompletedHistoricalImport = ephemeral.hasCompletedHistoricalImport,
            importProgress = ephemeral.importProgress,
            importError = ephemeral.importError,
            presetApplyError = ephemeral.presetApplyError,
            importInsightsSummary = ephemeral.importInsightsSummary,
            importResumeState = ephemeral.importResumeState,
            liftsValid = gymLiftsValid,
            showErrors = ephemeral.showErrors,
        ),
        reviewDraft = profile.toReviewProfileSection(specialization, lifts, preset),
        presetOptions = allGymPresetOptions(),
        generateState = ephemeral.generateState,
        generateErrorMessage = ephemeral.generateErrorMessage,
    )
}

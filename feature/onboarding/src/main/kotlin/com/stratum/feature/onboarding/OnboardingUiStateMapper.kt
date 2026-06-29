package com.stratum.feature.onboarding

import com.stratum.domain.onboarding.OnboardingStepCapabilities
import com.stratum.domain.onboarding.OnboardingWizardState
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.UserTrainingProfile

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
    capabilities: OnboardingStepCapabilities,
    importResumeScalars: ImportResumeScalars,
): OnboardingUiState {
    val step = current.currentStep.toWizardStepUi()
    val lifts = current.lifts
    val preset = current.selectedPreset
    val importing = ephemeral.importProgress?.let { progress ->
        progress.phase != ImportProgressPhaseUi.Complete &&
            progress.phase != ImportProgressPhaseUi.Failed
    } == true
    val canAdvance = !ephemeral.applyingPreset && !importing && capabilities.stepValid

    return OnboardingUiState(
        step = step,
        isComplete = current.isComplete,
        canAdvance = canAdvance,
        canGoBack = capabilities.canGoBack,
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
            importResumeScalars = importResumeScalars,
            liftsValid = capabilities.gymLiftsValid,
            showErrors = ephemeral.showErrors,
        ),
        reviewDraft = profile.toReviewProfileSection(specialization, lifts, preset),
        presetOptions = allGymPresetOptions(),
        generateState = ephemeral.generateState,
        generateProgress = ephemeral.generateProgress,
        generateErrorMessage = ephemeral.generateErrorMessage,
    )
}

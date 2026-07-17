package com.stratum.feature.onboarding

// Shared training-setup drafts live in :core:ui (onboarding + create program).
typealias ScheduleDraftUi = com.stratum.core.ui.trainingsetup.ScheduleDraftUi
typealias SpecializationDraftUi = com.stratum.core.ui.trainingsetup.SpecializationDraftUi
typealias MinimumVolumeDraftUi = com.stratum.core.ui.trainingsetup.MinimumVolumeDraftUi
typealias SessionLengthErrorKind = com.stratum.core.ui.trainingsetup.SessionLengthErrorKind

/**
 * Presentation model for the onboarding wizard. Domain types are mapped here; composables
 * never receive domain enums (ARCHITECTURE.md §4.3).
 */
data class OnboardingUiState(
    val step: WizardStepUi,
    val steps: List<WizardStepUi> = allWizardSteps(),
    val onboardingPath: OnboardingPathUi? = null,
    val isComplete: Boolean = false,
    val canAdvance: Boolean = false,
    val canGoBack: Boolean = false,
    val profileDraft: ProfileDraftUi = ProfileDraftUi.Empty,
    val scheduleDraft: ScheduleDraftUi = ScheduleDraftUi.Empty,
    val specializationDraft: SpecializationDraftUi = SpecializationDraftUi.Empty,
    val minimumVolumeDraft: MinimumVolumeDraftUi = MinimumVolumeDraftUi.Empty,
    val gymLiftsDraft: GymLiftsDraftUi = GymLiftsDraftUi.Empty,
    val reviewDraft: ReviewDraftUi = ReviewDraftUi.Empty,
    val presetOptions: List<GymPresetOptionUi> = emptyList(),
    val generateState: GenerateState = GenerateState.Idle,
    val generateProgress: ProgramGenerationProgressUi? = null,
    val generateMaxTimelineIndex: Int = 0,
    val generateErrorMessage: String? = null,
    val importProgramState: ImportProgramState = ImportProgramState.Idle,
    val importMaxTimelineIndex: Int = 0,
    /** Transient snackbar for step-navigation I/O failures (not validation). */
    val navigationError: String? = null,
) {
    val isFirst: Boolean get() = step.isFirst
    val isLast: Boolean get() = step.isLast

    companion object {
        val Initial = OnboardingUiState(
            step = allWizardSteps().first(),
            presetOptions = allGymPresetOptions(),
        )
    }
}

/**
 * Presentation mirror of the domain onboarding path. `null` while the user is in the common prefix
 * (before choosing at ChoosePath). Drives which summary cards the Review step shows and which
 * action (Generate vs Import) it offers.
 */
enum class OnboardingPathUi {
    Generate,
    Import,
}

/**
 * UI-shaped snapshot of the Profile step. Selections are presentation strings (the chip
 * labels) — the ViewModel maps them to/from the domain draft.
 */
data class ProfileDraftUi(
    val trainingAgeSelection: String? = null,
    val lastTrainedSelection: String? = null,
    val showErrors: Boolean = false,
) {
    val hasTrainingAgeError: Boolean
        get() = showErrors && trainingAgeSelection == null

    val hasLastTrainedError: Boolean
        get() = showErrors && lastTrainedSelection == null

    companion object {
        val Empty = ProfileDraftUi()
    }
}

/**
 * State of the Generate step (OS-7 D4). `Idle` shows the Generate CTA; `Loading` shows progress copy
 * and disables the CTA; `Error` offers retry + back-to-review. Success is implicit — the wizard's
 * `isComplete` flips and the existing `LaunchedEffect(state.isComplete)` consumes it.
 */
enum class GenerateState { Idle, Loading, Error }

/**
 * State of the "Import Existing" program flow on the Review step. `Idle` shows the Import CTA
 * above the summary; `Loading` replaces the body with a progress timeline; `Error` / `Rejected`
 * replace the body with a focused recovery pane (error card + Choose another file). `Rejected` is
 * for files the server determined are not a program. Success is implicit — the wizard's
 * `isComplete` flips just like generation.
 */
sealed interface ImportProgramState {
    data object Idle : ImportProgramState
    data class Loading(val phase: ImportProgramPhaseUi) : ImportProgramState
    data class Error(val message: String?) : ImportProgramState
    data class Rejected(val message: String?) : ImportProgramState
}

/** Presentation phases for program-template import (mirrors the domain enum without leaking it). */
enum class ImportProgramPhaseUi {
    Uploading,
    Sniffing,
    Analyzing,
    Converting,
    Parsing,
    MatchingCatalog,
    Complete,
}

/**
 * UI-shaped snapshot of one catalog lift for the toggle list. Kept for future post-onboarding
 * catalog management mapping in [GymLiftsDraftMapping].
 */
data class LiftSummaryUi(
    val id: String,
    val name: String,
    val patternLabel: String,
    val muscleSummary: String,
    val enabled: Boolean,
)

/**
 * Profile and schedule step callbacks for [OnboardingWizard].
 */
data class ProfileScheduleHandlers(
    val onTrainingAgeSelected: (String?) -> Unit,
    val onLastTrainedSelected: (String?) -> Unit,
    val onTrainingDayCountChanged: (Int) -> Unit = {},
    val onSessionPresetSelected: (String?) -> Unit,
    val onCustomSessionMinutesChange: (Int) -> Unit,
    val onSixDayCycleLengthSelected: (Int) -> Unit = {},
    val onTrainingSlotToggled: (Int) -> Unit = {},
)

/**
 * Choose-path fork callbacks. [onSelectScratch] is app-navigated Create Program (no domain path).
 */
data class ChoosePathHandlers(
    val onSelectGenerate: () -> Unit,
    val onSelectScratch: () -> Unit,
    val onSelectImport: () -> Unit,
)

/**
 * Bundle of the Minimum Volume step's event callbacks.
 */
data class MinimumVolumeHandlers(
    val onMuscleToggled: (String) -> Unit,
    val onSuggestedMuscleToggled: (String) -> Unit,
)

/**
 * Bundle of the Gym & Lifts step's event callbacks.
 */
data class GymLiftsHandlers(
    val onPresetSelected: (String?) -> Unit,
)

/**
 * UI-shaped snapshot of the Gym & Lifts step (OS-4b).
 */
data class GymLiftsDraftUi(
    val selectedPresetLabel: String? = null,
    val usedHistoricalImport: Boolean = false,
    val hasCompletedHistoricalImport: Boolean = false,
    val importProgress: ImportProgressUi? = null,
    val importError: String? = null,
    /** Server rejected the file as not a training log — distinct title/hint vs generic fail. */
    val importUnsupportedFile: Boolean = false,
    /** Furthest historical-import timeline step this run (monotonic). */
    val historicalImportMaxTimelineIndex: Int = 0,
    val presetApplyError: String? = null,
    val importInsightsSummary: ImportInsightsSummaryUi? = null,
    /** 1-based index of the file that failed during a multi-file import; enables resume UX. */
    val importResumeFileIndex: Int? = null,
    val importResumeFileCount: Int? = null,
    val importResumeFailedFileName: String? = null,
    val isValid: Boolean = false,
    val showErrors: Boolean = false,
) {
    val importComplete: Boolean
        get() =
            when (importProgress?.phase) {
                ImportProgressPhaseUi.Complete -> true
                ImportProgressPhaseUi.Failed -> false
                null ->
                    usedHistoricalImport &&
                        hasCompletedHistoricalImport &&
                        importError == null
                else -> false
            }

    val importFailed: Boolean
        get() = importProgress?.phase == ImportProgressPhaseUi.Failed

    val importResumable: Boolean
        get() = importFailed &&
            importResumeFileIndex != null &&
            importResumeFileCount != null &&
            importResumeFileCount > 1

    val importInProgress: Boolean
        get() = importProgress != null &&
            importProgress.phase != ImportProgressPhaseUi.Complete &&
            importProgress.phase != ImportProgressPhaseUi.Failed

    val showNoPresetError: Boolean
        get() = showErrors && !isValid && selectedPresetLabel == null && !usedHistoricalImport

    companion object {
        val Empty = GymLiftsDraftUi()
    }
}

/**
 * Read-only aggregate of every onboarding input, for the Review step (OS-5).
 */
data class ReviewDraftUi(
    val trainingAge: String? = null,
    val lastTrained: String? = null,
    val sessionMinutes: Int? = null,
    val specialization: List<String> = emptyList(),
    val minimumVolume: List<String> = emptyList(),
    val presetLabel: String? = null,
    val enabledLiftCount: Int = 0,
) {
    companion object {
        val Empty = ReviewDraftUi()
    }
}

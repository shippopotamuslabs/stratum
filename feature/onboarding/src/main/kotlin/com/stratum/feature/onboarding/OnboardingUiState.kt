package com.stratum.feature.onboarding

/**
 * Presentation model for the onboarding wizard. Domain types are mapped here; composables
 * never receive domain enums (ARCHITECTURE.md §4.3).
 */
data class OnboardingUiState(
    val step: WizardStepUi,
    val steps: List<WizardStepUi> = allWizardSteps(),
    val isComplete: Boolean = false,
    val canAdvance: Boolean = false,
    val canGoBack: Boolean = false,
    val profileDraft: ProfileDraftUi = ProfileDraftUi.Empty,
    val scheduleDraft: ScheduleDraftUi = ScheduleDraftUi.Empty,
    val specializationDraft: SpecializationDraftUi = SpecializationDraftUi.Empty,
    val gymLiftsDraft: GymLiftsDraftUi = GymLiftsDraftUi.Empty,
    val reviewDraft: ReviewDraftUi = ReviewDraftUi.Empty,
    val presetOptions: List<GymPresetOptionUi> = emptyList(),
    val generateState: GenerateState = GenerateState.Idle,
    val generateProgress: ProgramGenerationProgressUi? = null,
    val generateErrorMessage: String? = null,
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
 * UI-shaped snapshot of the Schedule step.
 */
data class ScheduleDraftUi(
    val selectedWeekdays: Set<String> = emptySet(),
    val sessionLengthSelection: String? = null,
    val customSessionMinutes: Int = DEFAULT_CUSTOM_SESSION_MINUTES,
    val showErrors: Boolean = false,
) {
    val resolvedSessionMinutes: Int?
        get() = when (sessionLengthSelection) {
            null -> null
            CUSTOM_KEY -> customSessionMinutes
            else -> sessionLengthSelection.toIntOrNull()
        }

    val hasWeekdaysError: Boolean
        get() = showErrors && selectedWeekdays.isEmpty()

    val sessionLengthErrorKind: SessionLengthErrorKind?
        get() {
            if (!showErrors) return null
            val minutes = resolvedSessionMinutes
            return when {
                sessionLengthSelection == null -> SessionLengthErrorKind.Required
                minutes == null -> SessionLengthErrorKind.NotANumber
                minutes < OnboardingValidationConstants.MIN_SESSION_MINUTES ->
                    SessionLengthErrorKind.TooShort
                minutes > OnboardingValidationConstants.MAX_SESSION_MINUTES ->
                    SessionLengthErrorKind.TooLong
                else -> null
            }
        }

    companion object {
        const val CUSTOM_KEY = "custom"
        val Empty = ScheduleDraftUi()
    }
}

enum class SessionLengthErrorKind {
    Required,
    CustomBlank,
    NotANumber,
    TooShort,
    TooLong,
}

/**
 * State of the Generate step (OS-7 D4). `Idle` shows the Generate CTA; `Loading` shows progress copy
 * and disables the CTA; `Error` offers retry + back-to-review. Success is implicit — the wizard's
 * `isComplete` flips and the existing `LaunchedEffect(state.isComplete)` consumes it.
 */
enum class GenerateState { Idle, Loading, Error }

/**
 * UI-shaped snapshot of the Specialization step (OS-3).
 */
data class SpecializationDraftUi(
    val selectedMuscleLabels: Set<String> = emptySet(),
    val showErrors: Boolean = false,
) {
    val specializedCount: Int get() = selectedMuscleLabels.size

    val atCap: Boolean get() = specializedCount >= OnboardingValidationConstants.MAX_SPECIALIZE

    val isValid: Boolean get() = specializedCount <= OnboardingValidationConstants.MAX_SPECIALIZE

    companion object {
        val Empty = SpecializationDraftUi()
    }
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
    val weekdays: List<String> = emptyList(),
    val sessionMinutes: Int? = null,
    val specialization: List<String> = emptyList(),
    val presetLabel: String? = null,
    val enabledLiftCount: Int = 0,
) {
    companion object {
        val Empty = ReviewDraftUi()
    }
}

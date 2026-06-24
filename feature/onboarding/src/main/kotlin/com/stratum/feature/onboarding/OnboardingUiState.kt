package com.stratum.feature.onboarding

import com.stratum.domain.lift.ValidateGymLiftsStepUseCase
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.onboarding.ValidateSpecializationStepUseCase

/**
 * Presentation model for the onboarding wizard. Domain `OnboardingStep` is mapped
 * to a presentation-friendly form here; composables never receive the domain type
 * (ARCHITECTURE.md §4.1).
 *
 * OS-2 adds per-step presentation drafts ([profileDraft], [scheduleDraft]) that the
 * Profile and Schedule screens render and mutate. OS-3 adds [specializationDraft].
 * OS-4 adds [gymLiftsDraft]. OS-5 adds [reviewDraft], a read-only aggregate of the prior
 * four inputs. These are UI-shaped (strings, boolean flags) — the ViewModel translates
 * them to/from the domain `UserTrainingProfile` / `SpecializationPreference` / lift
 * catalog.
 */
data class OnboardingUiState(
    val step: OnboardingStep,
    val isComplete: Boolean = false,
    val profileDraft: ProfileDraftUi = ProfileDraftUi.Empty,
    val scheduleDraft: ScheduleDraftUi = ScheduleDraftUi.Empty,
    val specializationDraft: SpecializationDraftUi = SpecializationDraftUi.Empty,
    val gymLiftsDraft: GymLiftsDraftUi = GymLiftsDraftUi.Empty,
    val reviewDraft: ReviewDraftUi = ReviewDraftUi.Empty,
) {
    val isFirst: Boolean get() = step.isFirst
    val isLast: Boolean get() = step.isLast

    /**
     * Whether the user may advance from the current step. The shared wizard Next button
     * is gated on this so invalid drafts cannot be left behind.
     */
    val canAdvance: Boolean
        get() = when (step) {
            OnboardingStep.Profile -> profileDraft.isValid
            OnboardingStep.Schedule -> scheduleDraft.isValid
            OnboardingStep.Specialization -> specializationDraft.isValid
            OnboardingStep.GymAndLifts -> gymLiftsDraft.isValid
            else -> step.next() != null
        }

    val canGoBack: Boolean get() = step.previous() != null

    companion object {
        val Initial = OnboardingUiState(step = OnboardingStep.First)
    }
}

/**
 * UI-shaped snapshot of the Profile step. Selections are presentation strings (the chip
 * labels) — the ViewModel maps them to/from the domain `TrainingAge` / `LastTrainedBucket`.
 */
data class ProfileDraftUi(
    val trainingAgeSelection: String? = null,
    val lastTrainedSelection: String? = null,
    val showErrors: Boolean = false,
) {
    val trainingAgeError: String?
        get() = if (showErrors && trainingAgeSelection == null) "Select your training age." else null

    val lastTrainedError: String?
        get() = if (showErrors && lastTrainedSelection == null) "Select when you last trained." else null

    val isValid: Boolean
        get() = trainingAgeSelection != null && lastTrainedSelection != null

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
    val customSessionInput: String = "",
    val showErrors: Boolean = false,
) {
    val resolvedSessionMinutes: Int?
        get() = sessionLengthSelection?.let { selection ->
            if (selection == CUSTOM_KEY) customSessionInput.toIntOrNull() else selection.toIntOrNull()
        }

    val weekdaysError: String? get() = if (showErrors && selectedWeekdays.isEmpty()) "Pick at least one day." else null

    val sessionLengthError: String?
        get() {
            if (!showErrors) return null
            val minutes = resolvedSessionMinutes
            return when {
                sessionLengthSelection == null -> "Choose a session length."
                sessionLengthSelection == CUSTOM_KEY && customSessionInput.isBlank() -> "Enter minutes."
                minutes == null -> "Session length must be a number."
                minutes < MIN_SESSION_MINUTES -> "Must be at least $MIN_SESSION_MINUTES minutes."
                minutes > MAX_SESSION_MINUTES -> "Must be $MAX_SESSION_MINUTES minutes or below."
                else -> null
            }
        }

    val isValid: Boolean
        get() = selectedWeekdays.isNotEmpty() &&
            resolvedSessionMinutes?.let { it in MIN_SESSION_MINUTES..MAX_SESSION_MINUTES } == true

    companion object {
        const val CUSTOM_KEY = "custom"
        const val MIN_SESSION_MINUTES = 15
        const val MAX_SESSION_MINUTES = 240
        val Empty = ScheduleDraftUi()
    }
}

/**
 * UI-shaped snapshot of the Specialization step (OS-3). [selectedMuscleLabels] are the chip
 * labels the user has toggled to Specialize. The step is optional — an empty selection (skip)
 * is valid. The cap is sourced from the domain use case so UI and validation agree.
 */
data class SpecializationDraftUi(
    val selectedMuscleLabels: Set<String> = emptySet(),
    val showErrors: Boolean = false,
) {
    val specializedCount: Int get() = selectedMuscleLabels.size

    /** True when the cap is reached — the screen disables further selection. */
    val atCap: Boolean get() = specializedCount >= MAX_SPECIALIZE

    val isValid: Boolean get() = specializedCount <= MAX_SPECIALIZE

    companion object {
        val MAX_SPECIALIZE: Int = ValidateSpecializationStepUseCase.MAX_SPECIALIZE
        val Empty = SpecializationDraftUi()
    }
}

/**
 * UI-shaped snapshot of one catalog lift for the toggle list. The [id] is the lift's slug/UUID
 * as a plain String (composables never see the domain `LiftId` value class). [patternLabel]
 * and [muscleSummary] are pre-rendered strings so the composable does no domain formatting.
 */
data class LiftSummaryUi(
    val id: String,
    val name: String,
    val patternLabel: String,
    val muscleSummary: String,
    val enabled: Boolean,
)

/**
 * Bundle of the Gym & Lifts step's event callbacks, passed as one parameter to the wizard shell
 * so [OnboardingWizard] stays under the parameter-count threshold as more steps land.
 */
data class GymLiftsHandlers(
    val onPresetSelected: (String?) -> Unit,
)

/**
 * UI-shaped snapshot of the Gym & Lifts step (OS-4b). Simplified to preset-only selection —
 * the full lift catalog management (search, toggle list, custom-lift form) is deferred to a
 * post-onboarding settings screen. [selectedPresetLabel] is the chip label of the chosen gym
 * preset, or null if the user hasn't picked one yet.
 *
 * The step requires a preset to be selected to advance; the preset bulk-enables its tagged
 * lifts so the catalog has at least [MIN_ENABLED] enabled lifts for generation.
 */
data class GymLiftsDraftUi(
    val selectedPresetLabel: String? = null,
    val showErrors: Boolean = false,
) {
    val isValid: Boolean get() = selectedPresetLabel != null

    /** Inline error surfaced when the user tries to advance without picking a preset. */
    val noPresetError: String?
        get() = if (showErrors && selectedPresetLabel == null) "Pick your gym to continue." else null

    companion object {
        val MIN_ENABLED: Int = ValidateGymLiftsStepUseCase.MIN_ENABLED
        val Empty = GymLiftsDraftUi()
    }
}

/**
 * Read-only aggregate of every onboarding input, for the Review step (OS-5). The ViewModel
 * derives this from the same domain streams that feed the editable drafts; nothing here is
 * mutable from the screen. All values are pre-rendered presentation strings/counts — no
 * domain types reach the composable (ARCHITECTURE.md §4.3).
 *
 * Nullable / empty fields mean "not yet answered". The Review screen renders a friendly
 * placeholder for those (e.g. an empty [specialization] reads as "Balanced"), so the summary
 * is never blank even mid-flow.
 *
 * @property trainingAge Training-age chip label (e.g. "4–7 years"), or null.
 * @property lastTrained Last-trained bucket label (e.g. "Within the last month"), or null.
 * @property weekdays Short weekday labels in calendar order (e.g. ["Mon", "Wed", "Fri"]).
 * @property sessionMinutes Resolved session length in minutes, or null.
 * @property specialization Specialized muscle display labels (e.g. ["Quads", "Chest"]); empty
 *   means the user skipped — the program stays maintenance-balanced.
 * @property presetLabel Selected gym preset display name, or null.
 * @property enabledLiftCount How many catalog lifts are currently enabled for generation.
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

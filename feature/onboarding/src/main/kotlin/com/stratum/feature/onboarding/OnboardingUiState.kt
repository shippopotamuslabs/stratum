package com.stratum.feature.onboarding

import com.stratum.domain.onboarding.OnboardingStep

/**
 * Presentation model for the onboarding wizard. Domain `OnboardingStep` is mapped
 * to a presentation-friendly form here; composables never receive the domain type
 * (ARCHITECTURE.md §4.1).
 *
 * OS-2 adds per-step presentation drafts ([profileDraft], [scheduleDraft]) that the
 * Profile and Schedule screens render and mutate. These are UI-shaped (strings,
 * boolean flags) — the ViewModel translates them to/from the domain `UserTrainingProfile`.
 */
data class OnboardingUiState(
    val step: OnboardingStep,
    val isComplete: Boolean = false,
    val profileDraft: ProfileDraftUi = ProfileDraftUi.Empty,
    val scheduleDraft: ScheduleDraftUi = ScheduleDraftUi.Empty,
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

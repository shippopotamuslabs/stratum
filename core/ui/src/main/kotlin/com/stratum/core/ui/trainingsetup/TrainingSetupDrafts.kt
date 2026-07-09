package com.stratum.core.ui.trainingsetup

/** Default custom session length when the user first picks Custom. */
const val DEFAULT_CUSTOM_SESSION_MINUTES = 90

/**
 * UI-shaped snapshot of the Schedule preferences step (weekdays + session length).
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
                minutes < TrainingSetupValidationConstants.MIN_SESSION_MINUTES ->
                    SessionLengthErrorKind.TooShort
                minutes > TrainingSetupValidationConstants.MAX_SESSION_MINUTES ->
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
 * UI-shaped snapshot of the Specialization preferences step.
 */
data class SpecializationDraftUi(
    val selectedMuscleLabels: Set<String> = emptySet(),
    val saturatedSpecializeWarningLabels: List<String> = emptyList(),
    val showErrors: Boolean = false,
) {
    val specializedCount: Int get() = selectedMuscleLabels.size

    val atCap: Boolean get() = specializedCount >= TrainingSetupValidationConstants.MAX_SPECIALIZE

    val isValid: Boolean get() = specializedCount <= TrainingSetupValidationConstants.MAX_SPECIALIZE

    companion object {
        val Empty = SpecializationDraftUi()
    }
}

/**
 * UI-shaped snapshot of the Minimum Volume preferences step.
 */
data class MinimumVolumeDraftUi(
    val selectedMuscleLabels: Set<String> = emptySet(),
    val excludedMuscleLabels: Set<String> = emptySet(),
    val suggestedMuscleLabels: Set<String> = emptySet(),
    val showErrors: Boolean = false,
) {
    val selectedCount: Int get() = selectedMuscleLabels.size

    val atCap: Boolean get() = selectedCount >= TrainingSetupValidationConstants.MAX_MINIMUM_VOLUME

    val isValid: Boolean get() = selectedCount <= TrainingSetupValidationConstants.MAX_MINIMUM_VOLUME

    companion object {
        val Empty = MinimumVolumeDraftUi()
    }
}

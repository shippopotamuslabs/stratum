package com.stratum.core.ui.trainingsetup

/** Default custom session length when the user first picks Custom. */
const val DEFAULT_CUSTOM_SESSION_MINUTES = 90

/** Soft-aim midpoints persisted as [com.stratum.domain.onboarding.UserTrainingProfile.sessionLengthMinutes]. */
const val SESSION_BAND_AIM_SHORT = 30
const val SESSION_BAND_AIM_MEDIUM = 50
const val SESSION_BAND_AIM_LONG = 75

/** UI band keys (segmented control). */
const val SESSION_BAND_KEY_SHORT = "20-40"
const val SESSION_BAND_KEY_MEDIUM = "40-60"
const val SESSION_BAND_KEY_LONG = "60-90"

/**
 * UI-shaped snapshot of the Schedule preferences step (weekdays + session length).
 */
data class ScheduleDraftUi(
    val selectedWeekdays: Set<String> = emptySet(),
    val sessionLengthSelection: String? = null,
    val customSessionMinutes: Int = DEFAULT_CUSTOM_SESSION_MINUTES,
    val showErrors: Boolean = false,
) {
    /** Soft-aim minutes written to the profile (band midpoint or custom value). */
    val resolvedSessionMinutes: Int?
        get() = when (sessionLengthSelection) {
            null -> null
            CUSTOM_KEY -> customSessionMinutes
            SESSION_BAND_KEY_SHORT -> SESSION_BAND_AIM_SHORT
            SESSION_BAND_KEY_MEDIUM -> SESSION_BAND_AIM_MEDIUM
            SESSION_BAND_KEY_LONG -> SESSION_BAND_AIM_LONG
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

        val BAND_KEYS: List<String> =
            listOf(SESSION_BAND_KEY_SHORT, SESSION_BAND_KEY_MEDIUM, SESSION_BAND_KEY_LONG, CUSTOM_KEY)

        /** Soft-aim minutes → band key for restoring UI from profile. */
        val AIM_TO_BAND_KEY: Map<Int, String> =
            mapOf(
                SESSION_BAND_AIM_SHORT to SESSION_BAND_KEY_SHORT,
                SESSION_BAND_AIM_MEDIUM to SESSION_BAND_KEY_MEDIUM,
                SESSION_BAND_AIM_LONG to SESSION_BAND_KEY_LONG,
            )
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

package com.stratum.feature.onboarding

import com.stratum.domain.importing.HistoricalImportResumeState
import com.stratum.domain.importing.ImportProgress

/**
 * Ephemeral wizard UI state that does not belong in persisted onboarding progress — validation
 * flags, import/generate progress, and in-flight affordances. Collapsed into one flow so
 * [OnboardingViewModel] avoids a nested [combine] pyramid as new steps add concerns.
 */
internal data class OnboardingEphemeralState(
    val showErrors: Boolean = false,
    val applyingPreset: Boolean = false,
    val importProgress: ImportProgress? = null,
    val importError: String? = null,
    val hasCompletedHistoricalImport: Boolean = false,
    val importInsightsSummary: ImportInsightsSummaryUi? = null,
    val importResumeState: HistoricalImportResumeState? = null,
    val generateState: GenerateState = GenerateState.Idle,
    val generateErrorMessage: String? = null,
    val presetApplyError: String? = null,
) {
    companion object {
        val Initial = OnboardingEphemeralState()
    }
}

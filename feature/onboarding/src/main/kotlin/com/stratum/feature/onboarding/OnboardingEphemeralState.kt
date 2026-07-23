package com.stratum.feature.onboarding

/**
 * Ephemeral wizard UI state that does not belong in persisted onboarding progress — validation
 * flags, import/generate progress, and in-flight affordances. Collapsed into one flow so
 * [OnboardingViewModel] avoids a nested [combine] pyramid as new steps add concerns.
 */
internal data class OnboardingEphemeralState(
    val showErrors: Boolean = false,
    val applyingPreset: Boolean = false,
    val importProgress: ImportProgressUi? = null,
    val importError: String? = null,
    /** True when the server rejected the file as not a training log (distinct Gym-card copy). */
    val importUnsupportedFile: Boolean = false,
    /** Furthest historical-import timeline step this run (monotonic). */
    val historicalImportMaxTimelineIndex: Int = 0,
    val hasCompletedHistoricalImport: Boolean = false,
    val importInsightsSummary: ImportInsightsSummaryUi? = null,
    val generateState: GenerateState = GenerateState.Idle,
    val generateProgress: ProgramGenerationProgressUi? = null,
    /** Furthest timeline step reached this run; keeps the UI monotonic when phases arrive quickly. */
    val generateMaxTimelineIndex: Int = 0,
    val generateErrorMessage: String? = null,
    val importProgramState: ImportProgramState = ImportProgramState.Idle,
    /** Furthest import timeline step reached this run (monotonic, mirrors generate). */
    val importMaxTimelineIndex: Int = 0,
    val presetApplyError: String? = null,
    /** Transient snackbar for step-navigation I/O failures (not validation). */
    val navigationError: String? = null,
    /** True after import-based MV suggestions were auto-applied once this wizard run. */
    val minimumVolumeImportPrefillApplied: Boolean = false,
    /**
     * Live schedule train-day count. Null means fall back to
     * [com.stratum.domain.onboarding.UserTrainingProfile.generationTrainingDayCount] or UI default.
     */
    val scheduleTrainingDayCount: Int? = null,
    /**
     * Live gen cycle-space (N+R). Null falls back to persisted profile generation fields.
     * Room also persists these on Generate schedule flush (process-death safe).
     */
    val generationCycleLength: Int? = null,
    val generationRestSlotIndices: Set<Int>? = null,
    /** Prefill for import weight-unit gate (mirrors Settings display preference). */
    val appWeightUnitIsKg: Boolean = false,
    val showImportWeightUnitPrompt: Boolean = false,
    val launchHistoricalImportPicker: Boolean = false,
) {
    companion object {
        val Initial = OnboardingEphemeralState()
    }
}

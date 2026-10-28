package com.stratum.domain.onboarding

/**
 * Persisted progress through the onboarding wizard.
 *
 * A single row exists per install (see `onboarding_progress` table). When
 * [completedAt] is non-null the user has finished onboarding and should not
 * re-enter the wizard.
 *
 * Step movement goes through the named transitions ([advancedTo], [completed],
 * [reopened]) instead of raw `copy()` at call sites; the use cases and repository
 * decide WHEN a transition happens, the model owns what each transition means.
 * A completion timestamp before [startedAt] is impossible and rejected here.
 *
 * Timestamps are epoch milliseconds to keep the domain module JVM-only with no
 * datetime dependency.
 */
data class OnboardingProgress(
    val currentStep: OnboardingStep,
    val startedAt: Long,
    val completedAt: Long?,
    /**
     * Durable Generate vs Import choice after [OnboardingStep.ChoosePath]. Null while still in the
     * common prefix (or after [OnboardingRepository.reopenWizard]). Survives process death so Review
     * does not re-derive path from empty [UserTrainingProfile.weekdays].
     */
    val selectedPath: OnboardingPath? = null,
) {
    init {
        require(completedAt == null || completedAt >= startedAt) {
            "completedAt $completedAt must not precede startedAt $startedAt"
        }
    }

    val isComplete: Boolean get() = completedAt != null

    /**
     * Moves to [step], clearing any completion (saving a step means the wizard is
     * in progress again). A null [selectedPath] argument keeps the durable path;
     * pass one explicitly to record a new path choice.
     */
    fun advancedTo(
        step: OnboardingStep,
        selectedPath: OnboardingPath? = this.selectedPath,
    ): OnboardingProgress = copy(
        currentStep = step,
        completedAt = null,
        selectedPath = selectedPath,
    )

    /** Marks the wizard complete at [atMillis], keeping the current step and path. */
    fun completed(atMillis: Long): OnboardingProgress = copy(completedAt = atMillis)

    /**
     * Reopens the wizard at [OnboardingStep.ChoosePath], clearing completion and the
     * durable path so Review cannot mis-route from a stale choice.
     */
    fun reopened(): OnboardingProgress = copy(
        currentStep = OnboardingStep.ChoosePath,
        completedAt = null,
        selectedPath = null,
    )

    companion object {
        /** The first persisted shape for an install, started at [startedAtMillis]. */
        fun started(
            step: OnboardingStep,
            startedAtMillis: Long,
            selectedPath: OnboardingPath? = null,
        ): OnboardingProgress = OnboardingProgress(
            currentStep = step,
            startedAt = startedAtMillis,
            completedAt = null,
            selectedPath = selectedPath,
        )
    }
}

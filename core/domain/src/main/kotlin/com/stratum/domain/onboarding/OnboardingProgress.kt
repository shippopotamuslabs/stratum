package com.stratum.domain.onboarding

/**
 * Persisted progress through the onboarding wizard.
 *
 * A single row exists per install (see `onboarding_progress` table). When
 * [completedAt] is non-null the user has finished onboarding and should not
 * re-enter the wizard.
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
    val isComplete: Boolean get() = completedAt != null
}

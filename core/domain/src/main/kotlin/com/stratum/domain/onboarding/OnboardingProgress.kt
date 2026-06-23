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
) {
    val isComplete: Boolean get() = completedAt != null
}

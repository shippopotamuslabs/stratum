package com.stratum.domain.onboarding

import kotlinx.coroutines.flow.Flow

/**
 * Port for reading and mutating onboarding progress.
 *
 * Implemented in `:core:data` (Room) and faked in `:core:testing`. Domain and
 * feature code depend only on this interface.
 */
interface OnboardingRepository {
    /**
     * Hot stream of the current onboarding progress. Emits `null` before the
     * wizard has been started for the first time.
     */
    fun observeProgress(): Flow<OnboardingProgress?>

    /**
     * Persist the current step. Called as the user advances through the wizard
     * so a kill mid-flow resumes on the same step.
     */
    suspend fun saveStep(step: OnboardingStep)

    /**
     * Mark onboarding complete. After this, [observeProgress] emits a progress
     * row with a non-null `completedAt`.
     */
    suspend fun markComplete()

    /**
     * Re-enter the wizard after completion so the user can edit choices and regenerate/re-import.
     * Clears [OnboardingProgress.completedAt] and resumes on [OnboardingStep.Review].
     */
    suspend fun reopenWizard()
}

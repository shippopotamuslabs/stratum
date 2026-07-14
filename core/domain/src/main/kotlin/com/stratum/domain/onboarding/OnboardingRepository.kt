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
     *
     * @param selectedPath when non-null, updates [OnboardingProgress.selectedPath]; when null,
     * preserves the existing durable path (if any).
     */
    suspend fun saveStep(step: OnboardingStep, selectedPath: OnboardingPath? = null)

    /**
     * Mark onboarding complete. After this, [observeProgress] emits a progress
     * row with a non-null `completedAt`.
     */
    suspend fun markComplete()

    /**
     * Re-enter the wizard after completion so the user can pick a program path again
     * (Generate / Build From Scratch / Import). Clears [OnboardingProgress.completedAt] and
     * resumes on [OnboardingStep.ChoosePath].
     */
    suspend fun reopenWizard()
}

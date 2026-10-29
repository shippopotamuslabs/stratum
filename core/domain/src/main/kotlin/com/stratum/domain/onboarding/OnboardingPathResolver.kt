package com.stratum.domain.onboarding


/**
 * Single source of truth for onboarding Generate vs Import path resolution.
 *
 * After gen schedule identity moved to cycle-space (not [UserTrainingProfile.weekdays]), Review
 * must **not** treat empty weekdays as Import. Prefer the durable [OnboardingProgress.selectedPath]
 * set when the user leaves [OnboardingStep.ChoosePath]; fall back to persisted generate cycle-space
 * on the profile when path was not stored (process-death mid-wizard / legacy rows).
 */
object OnboardingPathResolver {
    fun resolve(
        step: OnboardingStep,
        selectedPath: OnboardingPath?,
        profile: UserTrainingProfile? = null,
        /** Explicit train-day count from the live schedule UI (null when not on Generate). */
        trainingDayCount: Int? = null,
        restSlotIndices: Set<Int>? = null,
        cycleLength: Int? = null,
    ): OnboardingPath? =
        when (step) {
            OnboardingStep.Profile,
            OnboardingStep.GymAndLifts,
            OnboardingStep.ChoosePath,
            -> null
            OnboardingStep.Schedule,
            OnboardingStep.Specialization,
            OnboardingStep.MinimumVolume,
            -> OnboardingPath.Generate
            OnboardingStep.Review ->
                resolveReviewPath(
                    selectedPath = selectedPath,
                    profile = profile,
                    trainingDayCount = trainingDayCount,
                    restSlotIndices = restSlotIndices,
                    cycleLength = cycleLength,
                )
        }

    private fun resolveReviewPath(
        selectedPath: OnboardingPath?,
        profile: UserTrainingProfile?,
        trainingDayCount: Int?,
        restSlotIndices: Set<Int>?,
        cycleLength: Int?,
    ): OnboardingPath {
        if (selectedPath != null) return selectedPath
        return if (
            hasGenerateScheduleIdentity(
                profile = profile,
                trainingDayCount = trainingDayCount,
                restSlotIndices = restSlotIndices,
                cycleLength = cycleLength,
            )
        ) {
            OnboardingPath.Generate
        } else {
            OnboardingPath.Import
        }
    }

    /**
     * True when the user has a real Generate schedule identity — **not** a UI default train count
     * alone (Import Review must not look like Generate because the VM default is 4).
     */
    fun hasGenerateScheduleIdentity(
        profile: UserTrainingProfile?,
        trainingDayCount: Int? = null,
        restSlotIndices: Set<Int>? = null,
        cycleLength: Int? = null,
    ): Boolean {
        if (profile?.hasPersistedGenerateCycleSpace() == true) return true
        // Live schedule step: both length and rests present (materialized), or explicit N with rests.
        if (cycleLength != null && cycleLength > 0 && restSlotIndices != null) return true
        if (trainingDayCount != null && trainingDayCount > 0 && restSlotIndices != null) return true
        // Legacy rows that still carry weekday schedule identity only.
        if (profile != null && profile.weekdays.isNotEmpty()) return true
        return false
    }
}


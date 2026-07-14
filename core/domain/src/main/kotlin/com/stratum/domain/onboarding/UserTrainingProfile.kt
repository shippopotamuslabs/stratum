package com.stratum.domain.onboarding

/**
 * Captured during the Profile and Schedule onboarding steps (OS-2). All fields are
 * nullable so a draft can be persisted incrementally as the user advances and navigates
 * back. A single row exists per install.
 *
 * Validation lives in [ValidateProfileStepUseCase] / [ValidateScheduleStepUseCase] —
 * this model is purely a value container.
 */
data class UserTrainingProfile(
    val trainingAge: TrainingAge? = null,
    val lastTrained: LastTrainedBucket? = null,
    /**
     * Legacy weekday set — **not** generate schedule identity. Cleared on Generate schedule flush.
     * May still appear on old rows; path resolution prefers [generationCycleLength] / train count.
     */
    val weekdays: Set<Weekday> = emptySet(),
    val sessionLengthMinutes: Int? = null,
    /**
     * Persisted generate cycle-space (N + length + rests). Written when advancing past Schedule on
     * the Generate path so process death does not lose rest placement before Review / generate.
     */
    val generationTrainingDayCount: Int? = null,
    val generationCycleLength: Int? = null,
    val generationRestSlotIndices: Set<Int> = emptySet(),
) {
    companion object {
        val Empty = UserTrainingProfile()
    }
}

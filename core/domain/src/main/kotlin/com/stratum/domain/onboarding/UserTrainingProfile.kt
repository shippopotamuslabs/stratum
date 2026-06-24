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
    val weekdays: Set<Weekday> = emptySet(),
    val sessionLengthMinutes: Int? = null,
) {
    companion object {
        val Empty = UserTrainingProfile()
    }
}

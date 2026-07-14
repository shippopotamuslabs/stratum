package com.stratum.domain.onboarding

/**
 * Typed reasons the Profile step fails validation. These are not display strings —
 * presentation layers map them to user-facing copy (ARCHITECTURE.md §4.3).
 */
enum class ProfileValidationError {
    TrainingAgeRequired,
    LastTrainedRequired,
}

/**
 * Typed reasons the Schedule step fails validation. Not display strings.
 */
enum class ScheduleValidationError {
    /** No training-day count (explicit N, cycle-space, or legacy weekdays). */
    WeekdaysRequired,
    SessionLengthRequired,
    SessionLengthOutOfRange,
    /** Gen accepts at most [ValidateScheduleStepUseCase.MAX_TRAINING_DAYS] train sessions. */
    TooManyTrainingDays,
    /**
     * Legacy alias: cycle-space rules failed for the given train count.
     * Prefer [InvalidCycleLength] / [InvalidRestSlotIndices] for new code.
     */
    CycleSpaceRequiresSixTrainingDays,
    /**
     * Cycle length does not match rest rules for train count N
     * (N&lt;6 → length 7; N=6 → length 7 or 8).
     */
    InvalidCycleLength,
    /** Rest indices must match expected R and lie in `[0, cycleLength)`. */
    InvalidRestSlotIndices,
}

/** Outcome of validating the Profile step. */
sealed interface ProfileValidationResult {
    data object Valid : ProfileValidationResult
    data class Invalid(val errors: Set<ProfileValidationError>) : ProfileValidationResult
}

/** Outcome of validating the Schedule step. */
sealed interface ScheduleValidationResult {
    data object Valid : ScheduleValidationResult
    data class Invalid(val errors: Set<ScheduleValidationError>) : ScheduleValidationResult
}

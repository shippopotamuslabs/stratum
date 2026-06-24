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
    WeekdaysRequired,
    SessionLengthRequired,
    SessionLengthOutOfRange,
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

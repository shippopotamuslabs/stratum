package com.stratum.domain.onboarding

import javax.inject.Inject

/**
 * Validates the Schedule step of onboarding. Pure (no I/O).
 *
 * The user must pick at least one weekday and a session length within the custom range.
 * Preset session lengths (45 / 60 / 75) are always in range by construction.
 */
class ValidateScheduleStepUseCase @Inject constructor() {
    operator fun invoke(profile: UserTrainingProfile): ScheduleValidationResult {
        val errors = buildSet {
            if (profile.weekdays.isEmpty()) add(ScheduleValidationError.WeekdaysRequired)
            when {
                profile.sessionLengthMinutes == null ->
                    add(ScheduleValidationError.SessionLengthRequired)
                profile.sessionLengthMinutes < MIN_SESSION_MINUTES ||
                    profile.sessionLengthMinutes > MAX_SESSION_MINUTES ->
                    add(ScheduleValidationError.SessionLengthOutOfRange)
            }
        }
        return if (errors.isEmpty()) {
            ScheduleValidationResult.Valid
        } else {
            ScheduleValidationResult.Invalid(errors)
        }
    }

    companion object {
        const val MIN_SESSION_MINUTES = 15
        const val MAX_SESSION_MINUTES = 240
    }
}
